package dancingBallsPKG;

/**
 * an instance of this class represents a trio of distinct notes, with the 
 * primary representation being the interval between the first two and the 
 * last two, and the duration of the first and 2nd notes
 * 
 * consume this class by calling setvalues during transition
 * @author john
 *
 */
public class myNoteIntervalTuple {
	public myAudioManager mgr;
	public int ID;
	public static int idBase = 0;
	//where in the song this tuple begins
	public int stTime;
	//notes played as key # on piano;durations of each note in millis;
	private int[] notes,dur;
	//tuple -> first transition interval, dur of first note, 2nd transition interval, dur of 2nd note
	public int[] transTuple;
	//refs to next and previous tuples in 
	public myNoteIntervalTuple prev, next;

	public myNoteIntervalTuple(myAudioManager _mgr, int _stTime,int _initnote, myNoteIntervalTuple _last) {	
		mgr=_mgr;ID = idBase++;stTime = _stTime;
		notes = new int[3];
		dur = new int[2];
		notes[0]=_initnote;		
		transTuple = new int[4]; 
		next=this; 
		prev=_last; 
		if(prev != null) {		prev.next = this;}
	}
	
	public myNoteIntervalTuple setFirstTransition(int _note, int _transtime) {
		dur[0]=_transtime - stTime;
		notes[1] = _note;
		transTuple[0] = notes[1]-notes[0];
		transTuple[1] = dur[0];
		return this;
	}
	
	public myNoteIntervalTuple finishInterval(int _note,int _endTime) {
		notes[2]=_note;
		dur[1] = _endTime - dur[0]- stTime;
		transTuple[2] = notes[2]-notes[1];
		transTuple[3] = dur[1];	
		return this;	
	}
	
	public String toString() {
		String res = "(";
		for(int i=0;i<transTuple.length-1;++i) {	res +=transTuple[i]+", ";}
		res += transTuple[transTuple.length-1]+")";
		return res;
	}
	

}// class myNoteIntervalTuple
