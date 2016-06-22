package toberumono.wrf;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import toberumono.json.JSONBoolean;
import toberumono.json.JSONData;
import toberumono.json.JSONObject;
import toberumono.json.JSONString;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistNumber;
import toberumono.utils.files.TransferFileWalker;
import toberumono.wrf.scope.AbstractScope;
import toberumono.wrf.scope.InvalidVariableAccessException;
import toberumono.wrf.scope.ModuleScopedMap;
import toberumono.wrf.scope.NamedScopeValue;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.ComputedTiming;
import toberumono.wrf.timing.NamelistTiming;
import toberumono.wrf.timing.Timing;
import toberumono.wrf.modules.WRFModule;

import static toberumono.wrf.SimulationConstants.*;

public class Simulation extends AbstractScope<Scope> {
	private static final ExecutorService pool = Executors.newWorkStealingPool();
	
	private final Logger logger;
	private final JSONObject configuration;
	private final ScopedMap general, timing;
	private final Path working, resolver;
	private final Timing globalTiming;
	private final Map<String, Module> modules;
	private final Set<Module> disabledModules;
	private final ScopedMap source, active;
	private Integer doms;
	private final NamelistNumber interval_seconds;
	private boolean serialModuleExecution;
	
	public Simulation(Calendar base, Path resolver, JSONObject configuration, JSONObject general, JSONObject modules, JSONObject paths, JSONObject timing) throws IOException {
		super(null);
		this.resolver = resolver;
		this.configuration = configuration;
		this.general = ScopedMap.buildFromJSON(general, this);
		this.timing = ScopedMap.buildFromJSON(timing, this);
		logger = Logger.getLogger(SIMULATION_LOGGER_ROOT);
		logger.setLevel(Level.parse(general.get("logging-level").value().toString().toUpperCase()));
		source = new ScopedMap(this);
		active = new ScopedMap(this);
		disabledModules = new HashSet<>();
		this.modules = Collections.unmodifiableMap(parseModules(modules, paths));
		globalTiming = ((Boolean) getTimingMap().get("use-computed-times")) ? new ComputedTiming((ScopedMap) getTimingMap().get("global"), base, this)
				: new NamelistTiming(getModule("wrf").getNamelist().get("time_control"), this);
		working = constructWorkingDirectory(getResolver().resolve(getGeneral().get("working-directory").toString()), (Boolean) general.get("always-suffix").value());
		for (String name : this.modules.keySet())
			active.put(name, paths.containsKey(name) ? getWorkingPath().resolve(((Path) source.get(name)).getFileName()) : getWorkingPath().resolve(name));
		ScopedMap timestep = this.modules.containsKey("grib") && !disabledModules.contains(modules.get("grib"))
				? ScopedMap.buildFromJSON((JSONObject) ((JSONObject) configuration.get("grib")).get("timestep")) : null;
		interval_seconds = timestep != null ? new NamelistNumber(calcIntervalSeconds(timestep)) : null;
		doms = null;
	}
	
	@NamedScopeValue("timing")
	public Timing getTiming() {
		return globalTiming;
	}
	
	@NamedScopeValue("timing-map")
	public ScopedMap getTimingMap() {
		return timing;
	}
	
	@NamedScopeValue("general")
	public ScopedMap getGeneral() {
		return general;
	}
	
	@Deprecated
	@NamedScopeValue("parallel")
	public ScopedMap getParallel() {
		return ((WRFModule) getModule("wrf")).getParallel();
	}
	
	public Path getSourcePath(String module) {
		return (Path) source.get(module);
	}
	
	public Path getActivePath(String module) {
		return (Path) active.get(module);
	}
	
	public Path getResolver() {
		return resolver;
	}
	
	@NamedScopeValue("working-directory")
	public Path getWorkingPath() {
		return working;
	}
	
	public Module getModule(String name) {
		return modules.get(name);
	}
	
	@NamedScopeValue("doms")
	public Integer getDoms() throws IOException {
		if (doms == null)
			doms = ((Number) modules.get("wrf").getNamelist().get("domains").get("max_dom").get(0).value()).intValue();
		return doms;
	}
	
	public NamelistNumber getIntervalSeconds() {
		return interval_seconds;
	}
	
	@NamedScopeValue("serial-module-execution")
	public boolean isSerialModuleExecution() {
		return serialModuleExecution;
	}
	
	public Path constructWorkingDirectory(Path workingRoot, boolean always_suffix) throws IOException {
		Path active = Files.createDirectories(workingRoot).resolve("active"), root;
		try (FileChannel channel = FileChannel.open(active, StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock lock = channel.lock()) {
			String name = makeWPSDateString(getTiming().getStart()).replaceAll(":", "_"); //Having colons in the path messes up WRF, so... Underscores.
			try (Stream<Path> children = Files.list(workingRoot)) {
				int count = children.filter(p -> p.getFileName().toString().startsWith(name)).toArray().length;
				root = Files.createDirectories(workingRoot.resolve(always_suffix || count > 0 ? name + "+" + (count + 1) : name));
			}
		}
		return root;
	}
	
	public void linkModules() throws IOException {
		for (Module module : modules.values())
			module.linkToWorkingDirectory();
	}
	
	public void updateNamelists() throws IOException, InterruptedException {
		for (Module module : modules.values()) {
			module.updateNamelist();
			module.writeNamelist();
		}
	}
	
	private Map<String, Module> parseModules(JSONObject modules, JSONObject paths) {
		Map<String, Module> out = new LinkedHashMap<>();
		for (String name : modules.keySet()) {
			try {
				if (paths.containsKey(name))
					source.put(name, getResolver().resolve(paths.get(name).value().toString()));
				out.put(name, loadModule(name, modules));
			}
			catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				// TODO Deal with failures when loading modules
				e.printStackTrace();
			}
		}
		return out;
	}
	
	private Module loadModule(String name, JSONObject modules)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
		ScopedMap description = ScopedMap.buildFromJSON((JSONObject) modules.get(name), this); //We use this so that computed fields can be accessed here
		JSONObject parameters = condenseSubsections(name::equals, configuration, "configuration", Integer.MAX_VALUE);
		if (!parameters.containsKey(TIMING_FIELD_NAME))
			parameters.put(TIMING_FIELD_NAME, makeGenericInheriter());
		parameters.put("name", new JSONString(name));
		ModuleScopedMap moduleParameters = ModuleScopedMap.buildFromJSON(parameters);
		@SuppressWarnings("unchecked") Class<? extends Module> clazz = (Class<? extends Module>) Class.forName(description.get("class").toString());
		Constructor<? extends Module> constructor = clazz.getConstructor(ModuleScopedMap.class, Simulation.class);
		Module m = constructor.newInstance(moduleParameters, this);
		if (description.containsKey("execute") && !((Boolean) description.get("execute")))
			disabledModules.add(m);
		return m;
	}
	
	/**
	 * Executes the {@link Module Modules} loaded in the {@link Simulation}
	 * 
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws InterruptedException
	 *             if any of the {@link Module} processes are interrupted
	 */
	public void executeModules() throws IOException, InterruptedException {
		List<Module> remaining = modules.values().stream().filter(mod -> !disabledModules.contains(mod)).collect(Collectors.toList());
		Set<Module> completed = new HashSet<>();
		while (remaining.size() > 0) {
			List<Module> runnable = new ArrayList<>();
			for (Iterator<Module> iter = remaining.iterator(); iter.hasNext();) {
				Module current = iter.next();
				if (completed.containsAll(current.getDependencies())) {
					runnable.add(current);
					iter.remove();
				}
			}
			if (runnable.size() == 0)
				break;
			if (isSerialModuleExecution()) {
				for (Module module : runnable)
					completed.add(executeModule(module));
			}
			else {
				List<Future<Module>> running = runnable.stream().map(module -> pool.submit(() -> executeModule(module))).collect(Collectors.toList());
				for (Future<Module> future : running) {
					try {
						completed.add(future.get());
					}
					catch (ExecutionException e) {
						if (e.getCause() instanceof IOException)
							throw (IOException) e.getCause();
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	/**
	 * Executes a single {@link Module} and handles keep-logs and cleanup.
	 * 
	 * @param module
	 *            the {@link Module} to execute
	 * @return the {@link Module} passed to {@code Module} (for compatibility with {@link Callable})
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws InterruptedException
	 *             if the process is interrupted
	 */
	protected Module executeModule(Module module) throws IOException, InterruptedException {
		module.execute();
		if ((Boolean) general.get("keep-logs"))
			Files.walkFileTree(getActivePath(module.getName()),
					new TransferFileWalker(getWorkingPath(), Files::move, p -> p.getFileName().toString().toLowerCase().endsWith(".log"), p -> true, null, null, true));
		if ((Boolean) general.get("cleanup"))
			module.cleanUp();
		return module;
	}
	
	private static int calcIntervalSeconds(ScopedMap timestep) {
		int out = ((Number) timestep.get("seconds")).intValue();
		out += ((Number) timestep.get("minutes")).intValue() * 60;
		out += ((Number) timestep.get("hours")).intValue() * 60 * 60;
		out += ((Number) timestep.get("days")).intValue() * 24 * 60 * 60;
		return out;
	}
	
	/**
	 * Converts the date in the given {@link Calendar} to a WPS {@link Namelist} file date string
	 * 
	 * @param cal
	 *            a {@link Calendar}
	 * @return a date string usable in a WPS {@link Namelist} file
	 */
	public static final String makeWPSDateString(Calendar cal) {
		return String.format(Locale.US, "%d-%02d-%02d_%02d:%02d:%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
				cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));
	}
	
	public static final JSONObject makeGenericInheriter() {
		JSONObject out = new JSONObject();
		out.put("inherit", new JSONBoolean(true));
		return out;
	}
	
	@Override
	public boolean hasValueByName(String name) {
		if (getModule(name) != null)
			return true;
		switch (name) {
			case "sim":
			case "simulation":
			case "source-paths":
			case "active-paths":
				return true;
			default:
				return super.hasValueByName(name);
		}
	}
	
	@Override
	public Object getValueByName(String name) throws InvalidVariableAccessException {
		Object out = getModule(name);
		if (out != null)
			return out;
		switch (name) {
			case "sim":
			case "simulation":
				return this;
			case "source-paths":
				return source;
			case "active-paths":
				return active;
			default:
				return super.getValueByName(name);
		}
	}
	
	private static JSONObject condenseSubsections(Predicate<String> lookingFor, JSONObject root, String rootName, int maxDepth) {
		JSONObject out = condenseSubsections(new JSONObject(), lookingFor, root, rootName, maxDepth - 1);
		out.clearModified();
		return out;
	}
	
	private static JSONObject condenseSubsections(JSONObject condensed, Predicate<String> lookingFor, JSONObject container, String containerName, int remainingDepth) {
		for (Entry<String, JSONData<?>> e : container.entrySet()) {
			if (lookingFor.test(e.getKey()))
				condensed.put(containerName, e.getValue());
			else if (remainingDepth > 0 && e.getValue() instanceof JSONObject)
				condenseSubsections(condensed, lookingFor, (JSONObject) e.getValue(), e.getKey(), remainingDepth - 1);
		}
		return condensed;
	}
	
	public static Simulation initSimulation(JSONObject configuration, Path resolver) throws IOException {
		Calendar base = Calendar.getInstance();
		//Extract configuration file sections
		JSONObject general = (JSONObject) configuration.get("general");
		JSONObject module = (JSONObject) configuration.get("module");
		JSONObject path = (JSONObject) configuration.get("path");
		JSONObject timing = (JSONObject) configuration.get("timing");
		
		return new Simulation(base, resolver, configuration, general, module, path, timing);
	}
}
