package toberumono.wrf.timing.duration;

import java.util.logging.Logger;

import toberumono.json.JSONObject;
import toberumono.wrf.timing.TimingComponent;

public abstract class Duration extends TimingComponent<Duration> {
	
	public Duration(JSONObject parameters, Duration parent) {
		super(parameters, parent, Logger.getLogger("Duration"));
	}
}
