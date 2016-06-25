package toberumono.wrf;

import java.nio.file.Path;
import java.nio.file.Paths;

public class WRFRunnerCommandLineArguments {
	private final Path configurationPath;
	private final boolean cacheUpdates, ignoreUpgradeProblems, performInteractiveUpgrade;
	
	public WRFRunnerCommandLineArguments(String[] args) {
		Path configurationPath = Paths.get("configuration.json");
		boolean cacheUpdates = false, ignoreUpgradeProblems = false, performInteractiveUpgrade = false;
		for (String arg : args) {
			switch (arg) {
				case "--no-upgrade-writing":
					cacheUpdates = true;
					break;
				case "--ignore-upgrade-problems":
					ignoreUpgradeProblems = true;
					break;
				case "--interactive-upgrade":
					performInteractiveUpgrade = true;
					break;
				default:
					configurationPath = Paths.get(arg);
			}
		}
		this.configurationPath = configurationPath;
		this.cacheUpdates = cacheUpdates;
		this.ignoreUpgradeProblems = ignoreUpgradeProblems;
		this.performInteractiveUpgrade = performInteractiveUpgrade;
	}
	
	public WRFRunnerCommandLineArguments(Path configurationPath, boolean cacheUpdates, boolean ignoreUpgradeProblems, boolean performInteractiveUpgrade) {
		this.configurationPath = configurationPath;
		this.cacheUpdates = cacheUpdates;
		this.ignoreUpgradeProblems = ignoreUpgradeProblems;
		this.performInteractiveUpgrade = performInteractiveUpgrade;
	}
	
	/**
	 * @return the {@link Path} to the configuration file
	 */
	public Path getConfigurationPath() {
		return configurationPath;
	}
	
	/**
	 * @return the updates to the configuration file should be written back to disk
	 */
	public boolean cacheUpdates() {
		return cacheUpdates;
	}
	
	/**
	 * @return the whether the {@link Simulation} should continue with potential upgrade problems should
	 */
	public boolean ignoreUpgradeProblems() {
		return ignoreUpgradeProblems;
	}
	
	/**
	 * @return the whether the upgrade problems should be resolved interactively
	 */
	public boolean isPerformInteractiveUpgrade() {
		return performInteractiveUpgrade;
	}
}
