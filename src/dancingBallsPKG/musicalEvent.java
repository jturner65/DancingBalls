package dancingBallsPKG;

//deciphered musical events (note) orderable by start time - 
//might be result of multiple midi events (note on, note off, pitch bend, etc)
public abstract class musicalEvent implements Comparable<musicalEvent>{
	//start of event - this value is the ordering value for this event
	//in midi ticks
	public int stTime;
	protected int duration, endTime;
	
	public musicalEvent(int _stTime) { 
		stTime = _stTime;
		duration = 0; endTime = stTime;		
	}

	public void setEndTime(int _et) {
		setEndTimeIndiv(_et);		//anything to be done for specific type of musical event when ending time is set
		endTime = _et;
		duration = endTime - stTime;
	}
	protected abstract void setEndTimeIndiv(int _et);
	
	@Override
	public int compareTo(musicalEvent othrEv) {
		if(stTime == othrEv.stTime) {return 0;}	//TODO change to check 2nd tier of ordering ?
		return (stTime > othrEv.stTime ? 1 : -1);
	}
	
}//class midiEvent

//event description for a musical note - should be long to a single channel
class musicalNote extends musicalEvent{
	public nValType note;
	public int octave;
	//volume of note
	public int vel;
	
	public int channel;//corresponds to an instrument
	
	public musicalNote(int _stTime, int _chan, int _midiVal, int _vel) {
		super(_stTime);
		//_midiVal is int value of note - idx 1 in msgbytes
		note = nValType.getVal((_midiVal % 12));
		octave = _midiVal / 12 - 1;//middle C (C4) is midi val 60
		channel = _chan;
		vel = _vel;		
	}

	@Override
	protected void setEndTimeIndiv(int _et) {
		// TODO Auto-generated method stub
		
	}
}//class musicalNote


//environment will be key signature, or time sig, or tempo
//this will be global accross all channels for the stated duration
abstract class musicalEnvironment extends musicalEvent{
	public scoreEnvVal type;	
	public musicalEnvironment(int _stTime, scoreEnvVal _type) {
		super(_stTime);
		type=_type;
	}
	
	
	//abstract methods for accessing environment for data - when notes are used, the current environment of key sig, time sig, and tempo, will be queried
	
}//musicalEnvironment

class keySigEnv extends musicalEnvironment{
	keySigVals keySig;

	public keySigEnv(int _stTime, keySigVals _ks) {
		super(_stTime, scoreEnvVal.keySig);
		keySig = _ks;
	}

	@Override
	protected void setEndTimeIndiv(int _et) {
		// TODO Auto-generated method stub
		
	}
	
}//class keySigEnv

class timeSigEnv extends musicalEnvironment{
	int num;
	int denom;
	public timeSigEnv(int _stTime) {
		super(_stTime, scoreEnvVal.timeSig);
		// TODO Auto-generated constructor stub
	}
	@Override
	protected void setEndTimeIndiv(int _et) {
		// TODO Auto-generated method stub
		
	}
	
}//class timeSigEnv


class tempoEnv extends musicalEnvironment{
	public int midiTempo;
	public float BPM;
	
	public tempoEnv(int _stTime, int _midiTempo) {
		super(_stTime, scoreEnvVal.tempo);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void setEndTimeIndiv(int _et) {
		// TODO Auto-generated method stub
		
	}
	
	
}





//class to hold a single training example data point
//this class will provide a string-representation of itself for saving feature data
class audioExampleData {
	
	public audioExampleData() {
		
	}
	
	
	
	
	
}//audioExampleData