package toberumono.wrf.timing;

import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistSection;
import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.AbstractScope;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.timing.clear.Clear;
import toberumono.wrf.timing.duration.Duration;
import toberumono.wrf.timing.duration.NamelistDuration;
import toberumono.wrf.timing.offset.Offset;
import toberumono.wrf.timing.round.Round;

import static toberumono.wrf.SimulationConstants.*;

/**
 * Implementation of {@link Timing} that uses static data from a {@link Namelist} file instead of computing the timing data at runtime.
 * 
 * @author Toberumono
 */
public class NamelistTiming extends AbstractScope<Scope> implements Timing {
	private static final List<String> TIMING_FIELD_SINGULAR_NAMES = TIMING_FIELD_NAMES.stream().map(s -> s.substring(0, s.length() - 1)).collect(Collectors.toList());
	
	private final Calendar base;
	private final Calendar start, end;
	private final Offset offset;
	private final Round round;
	private final Duration duration;
	private final Clear clear;
	
	/**
	 * Constructs a {@link NamelistTiming} instance using the given time_control section.
	 * 
	 * @param timeControl
	 *            the time_control section of a {@link Namelist} file as a {@link NamelistSection}
	 * @param parent
	 *            the parent {@link Scope} (this is not used within the class - it is exclusively for consistency with the tree structure
	 */
	public NamelistTiming(NamelistSection timeControl, Scope parent) { //No need for lazy computation - everything is either Disabled or independent
		super(parent);
		this.base = Calendar.getInstance();
		timecontrolParser(getBase(), timeControl, "start");
		offset = WRFRunnerComponentFactory.getDisabledComponentInstance(Offset.class, null, this);
		round = WRFRunnerComponentFactory.getDisabledComponentInstance(Round.class, null, this);
		duration = new NamelistDuration(timeControl, this);
		clear = WRFRunnerComponentFactory.getDisabledComponentInstance(Clear.class, null, this);
		start = getOffset().apply(getRound().apply(getBase()));
		end = getDuration().apply(getStart());
	}
	
	private void timecontrolParser(Calendar cal, NamelistSection tc, String prefix) {
		prefix = prefix.endsWith("_") ? prefix : prefix + "_";
		String name;
		for (int i = 0; i < TIMING_FIELD_IDS.size(); i++) {
			name = prefix + TIMING_FIELD_SINGULAR_NAMES.get(i);
			if (tc.containsKey(name))
				cal.set(TIMING_FIELD_IDS.get(i), ((Number) tc.get(name).get(0).value()).intValue());
		}
	}
	
	@Override
	public Calendar getBase() {
		return base;
	}
	
	@Override
	public Calendar getStart() {
		return start;
	}
	
	@Override
	public Calendar getEnd() {
		return end;
	}
	
	@Override
	public Offset getOffset() {
		return offset;
	}
	
	@Override
	public Round getRound() {
		return round;
	}
	
	@Override
	public Duration getDuration() {
		return duration;
	}
	
	@Override
	public Clear getClear() {
		return clear;
	}
}
