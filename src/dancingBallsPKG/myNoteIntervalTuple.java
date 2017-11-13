package dancingBallsPKG;

/**
 * an instance of this class represents a trio of distinct notes, with the 
 * primary representation being the interval between the first two and the 
 * last two, and the duration of the first and 2nd notes
 * @author john
 *
 */
public class myNoteIntervalTuple {
	public myAudioManager mgr;
	public int ID;
	public static int idBase = 0;
	//where in the song this tuple begins
	public int stTime;
	//notes played as key # on piano;durations of each note in millis;tuple -> first transition interval, dur of first note, 2nd transition interval, dur of 2nd note
	private int[] notes,dur,transTuple;
	//refs to next and previous tuples in 
	public myNoteIntervalTuple prev, next;

	public myNoteIntervalTuple(myAudioManager _mgr) {	mgr=_mgr;ID = idBase++;transTuple = new int[4]; next=this; prev=this;}
	
	//notes has 3 values, dur has 2 values, returns a reference to this obj
	public myNoteIntervalTuple setValues(int[] _notes, int[] _dur, int _stTime, myNoteIntervalTuple _last) {
		notes = _notes; dur=_dur; stTime = _stTime;
		transTuple[0] = notes[1]-notes[0];
		transTuple[1] = dur[0];
		transTuple[2] = notes[2]-notes[1];
		transTuple[3] = dur[1];	
		prev = _last;
		prev.next = this;
		return this;
	}//setValues

}// class myNoteIntervalTuple
