package toberumono.wrf.timing;

import java.util.Calendar;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.clear.Clear;
import toberumono.wrf.timing.duration.Duration;
import toberumono.wrf.timing.offset.Offset;
import toberumono.wrf.timing.rounding.Rounding;

public class ComputedTiming extends TimingScope<Scope> implements Timing {
	private final ReentrantLock computationLock;
	
	private Calendar base, start, end;
	private Offset offset;
	private Rounding rounding;
	private Duration duration;
	private Clear clear;
	private boolean appliedClear;
	
	public ComputedTiming(ScopedMap parameters, Calendar base, Scope parent) {
		super(parameters, parent);
		if (!(parent instanceof Timing))
			Objects.requireNonNull(base, "The base Calendar cannot be null");
		computationLock = new ReentrantLock();
		start = end = null;
		offset = null;
		rounding = null;
		duration = null;
		clear = null;
		appliedClear = false;
		this.base = base;
	}
	
	public ComputedTiming(ScopedMap parameters, Scope parent) {
		this(parameters, null, parent);
	}
	
	@Override
	public Calendar getBase() {
		if (base == null) {
			try {
				computationLock.lock();
				if (base == null) { //Have to re-check for synchronization
					base = getClear().apply((getParent() instanceof Timing) ? ((Timing) getParent()).getBase() : null);
					appliedClear = true;
				}
			}
			finally {
				computationLock.unlock();
			}
		}
		if (!appliedClear) {
			try {
				computationLock.lock();
				if (!appliedClear) { //Have to re-check for synchronization
					base = getClear().apply(base);
					appliedClear = true;
				}
			}
			finally {
				computationLock.unlock();
			}
		}
		return base;
	}
	
	@Override
	public Calendar getStart() {
		if (start != null)
			return start;
		try {
			computationLock.lock();
			if (start == null)
				start = getOffset().apply(getRounding().apply(getBase()));
		}
		finally {
			computationLock.unlock();
		}
		return start;
	}
	
	@Override
	public Calendar getEnd() {
		if (end != null)
			return end;
		try {
			computationLock.lock();
			if (end == null)
				end = getDuration().apply(getStart());
		}
		finally {
			computationLock.unlock();
		}
		return end;
	}
	
	@Override
	public Offset getOffset() {
		if (offset != null)
			return offset;
		try {
			computationLock.lock();
			if (offset == null)
				offset = WRFRunnerComponentFactory.generateComponent(Offset.class, (ScopedMap) getParameters().get("offset"),
						(getParent() instanceof Timing) ? ((Timing) getParent()).getOffset() : null);
		}
		finally {
			computationLock.unlock();
		}
		return offset;
	}
	
	@Override
	public Rounding getRounding() {
		if (rounding != null)
			return rounding;
		try {
			computationLock.lock();
			if (rounding == null)
				rounding = WRFRunnerComponentFactory.generateComponent(Rounding.class, (ScopedMap) getParameters().get("rounding"),
						(getParent() instanceof Timing) ? ((Timing) getParent()).getRounding() : null);
		}
		finally {
			computationLock.unlock();
		}
		return rounding;
	}
	
	@Override
	public Duration getDuration() {
		if (duration != null)
			return duration;
		try {
			computationLock.lock();
			if (duration == null)
				duration = WRFRunnerComponentFactory.generateComponent(Duration.class, (ScopedMap) getParameters().get("duration"),
						(getParent() instanceof Timing) ? ((Timing) getParent()).getDuration() : null);
		}
		finally {
			computationLock.unlock();
		}
		return duration;
	}
	
	@Override
	public Clear getClear() {
		if (clear != null)
			return clear;
		try {
			computationLock.lock();
			if (clear == null)
				clear = WRFRunnerComponentFactory.generateComponent(Clear.class, (ScopedMap) getParameters().get("clear"), (getParent() instanceof Timing) ? ((Timing) getParent()).getClear() : null);
		}
		finally {
			computationLock.unlock();
		}
		return clear;
	}
}