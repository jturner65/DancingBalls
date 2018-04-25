package dancingBallsPKG;

import java.util.concurrent.*;

import javax.sound.midi.*;

public class myMidiSongData {
	//general midi patch names for program change
	public static final String[] GMPatches = new String[] {
			"Acoustic Grand Piano","Bright Acoustic Piano","Electric Grand Piano","Honky-tonk Piano", 
			"Electric Piano 1","Electric Piano 2","Harpsichord","Clavinet","Celesta","Glockenspiel","Music Box", 
			"Vibraphone","Marimba","Xylophone","Tubular Bells","Dulcimer","Drawbar Organ","Percussive Organ", 
			"Rock Organ", "Church Organ", "Reed Organ", "Accordion", "Harmonica", "Tango Accordion", "Acoustic Guitar (nylon)", 
			"Acoustic Guitar (steel)", "Electric Guitar (jazz)", "Electric Guitar (clean)", "Electric Guitar (muted)", 
			"Overdriven Guitar","Distortion Guitar","Guitar harmonics","Acoustic Bass","Electric Bass (finger)","Electric Bass (pick)",
			"Fretless Bass", "Slap Bass 1","Slap Bass 2","Synth Bass 1","Synth Bass 2","Violin","Viola","Cello","Contrabass",
			"Tremolo Strings","Pizzicato Strings", "Orchestral Harp","Timpani","String Ensemble 1","String Ensemble 2","SynthStrings 1", 
			"SynthStrings 2","Choir Aahs","Voice Oohs","Synth Voice","Orchestra Hit","Trumpet","Trombone","Tuba","Muted Trumpet",
			"French Horn","Brass Section","SynthBrass 1","SynthBrass 2","Soprano Sax","Alto Sax","Tenor Sax","Baritone Sax","Oboe",
			"English Horn","Bassoon","Clarinet","Piccolo","Flute","Recorder","Pan Flute","Blown Bottle","Shakuhachi","Whistle","Ocarina",
			"Lead 1 (square)","Lead 2 (sawtooth)","Lead 3 (calliope)","Lead 4 (chiff)","Lead 5 (charang)","Lead 6 (voice)","Lead 7 (fifths)",
			"Lead 8 (bass + lead)","Pad 1 (new age)","Pad 2 (warm)","Pad 3 (polysynth)","Pad 4 (choir)","Pad 5 (bowed)","Pad 6 (metallic)",
			"Pad 7 (halo)","Pad 8 (sweep)","FX 1 (rain)","FX 2 (soundtrack)","FX 3 (crystal)","FX 4 (atmosphere)", "FX 5 (brightness)",
			"FX 6 (goblins)","FX 7 (echoes)","FX 8 (sci-fi)","Sitar","Banjov","Shamisen","Kotov","Kalimba","Bag pipe","Fiddle","Shanai",
			"Tinkle Bell","Agogo","Steel Drums", "Woodblock","Taiko Drum","Melodic Tom","Synth Drum","Reverse Cymbal","Guitar Fret Noise",
			"Breath Noise","Seashore","Bird Tweet","Telephone Ring","Helicopter","Applause","Gunshot"}; 
	
	//whether to use full not value or just not start value for rhythm histogram
	private static final boolean useFullNoteVol = false;
	
	public myMidiFileAnalyzer mfa;
	//length of sequence in time
	public final long tickLen;
	//length of sequence data in bytes, 
	public final int byteLen;
	//type of midi file
	public final int type;
	//# of midi tracks
	public final int numTracks;

	//composer of this track, based on file listing
	public final String composer;
	//title
	public final String title;
	//all trackdata objects for this song
	public myMidiTrackData[] midiTracks;
	
	//want to store all data per channel
	public myMidiChannel[] midiChans;
	
	//song-wide variables - key sig, time sig, tempo, etc
	public songState state;
	//this song has been processed
	public boolean procDone;
	//map of all note volumes throughout song, for rhythm analysis
	public ConcurrentSkipListMap<Long,Float> noteHistogram;
	private float maxNoteLvl;
	
	public myMidiSongData (myMidiFileAnalyzer _mfa, MidiFileFormat _fmt, Sequence _seq) {
		mfa=_mfa;
		byteLen = _fmt.getByteLength();
		//duration of sequence in midi ticks
		tickLen = _seq.getTickLength();
		//float divType = format.getDivisionType();
		//all files are divType 0.0 -> PPQ: The tempo-based timing type, for which the resolution is expressed in pulses (ticks) per quarter note
		type = _fmt.getType();
		composer = mfa.fileListing.composerName;
		title =  mfa.fileListing.dispName;
		//if type == 0 then only 1 track
		Track[] tracks = _seq.getTracks();
		numTracks = tracks.length;	
		midiTracks = new myMidiTrackData[numTracks];
		noteHistogram = new ConcurrentSkipListMap<Long, Float>();
		//used to monitor min and max lvl of notes
		maxNoteLvl = -1;
		
		procDone = false;
		
		midiChans = new myMidiChannel[16];//never more than 16 channels
		//add ref to array, but array needs to be made with state ref
		state = new songState(this, midiChans);
		for(int i=0; i<midiChans.length;++i) {	midiChans[i] = new myMidiChannel(this, state, i);	}
		//when state is updated, it should update midiChans
//		if(3== mfa.thdIDX) {
//		System.out.println("Thd IDX : " + mfa.thdIDX + " | Composer : " +composer + "\tSong Name : " + title +"\tFormat info : byte len : " + byteLen+" | midi type : " + type + " | # of tracks  : "+ numTracks);
//		}
		for(int i=0;i<numTracks;++i) {
			midiTracks[i] = new myMidiTrackData(this, state, midiChans, tracks[i], i);
			midiTracks[i].procEvents();
		}
		finalizeSong();
	}//ctor	
	
	//used to build message from bytes in midi messages
	public String buildStrFromByteAra(byte[] msgBytes, int stIdx) {
		//stIdx might need to be 1 less?
		String res = "";
		if(stIdx >= msgBytes.length) {return res;} 
		res += "\"";
		for(int b=stIdx;b<msgBytes.length;++b) {
			Character c = (char)(msgBytes[b]& 0xFF);
			String add = "";
	        if ((c < ' ') || ((c > '~') && (c <= 160))) {	add += '\\'+ c;} 
	        else {
	            if (c == '"') {	add += '"';} else if (c == '\\') {	add +='\\';}
				add += c;
			}			
	        res += c;
		}	
		res += "\"";
		return res;
	}//buildStrFromByteAra
	
	//build a volume histogram of all the notes in this song, so that the rhythm can be inferred
	public void addToRhythmHist(midiNoteData _note) {
		if (useFullNoteVol)  {addFullNoteToRhythmHist(_note); } else { addNoteStToRhythmHist(_note); }
	}//	addToRhythmHist
	
	//this will add the note's level for the duration of the note to the rhythm histogram
	private void addFullNoteToRhythmHist(midiNoteData _note) {
		//this will hold a record of all note volumes that have changed and when they change.  
		ConcurrentSkipListMap<Integer, Integer> volVals = _note.getAllNoteVols();
		//key is relative time from start of note; value is note volume at that location if it is different.  
		for(int i=0;i<_note.noteDur; ++i) {
			Long noteAbsLoc = i + _note.stTime;					//time in song
			Float oldVol = noteHistogram.get(noteAbsLoc);		//existing volume at this location in song
			if(null == oldVol) {		oldVol = 0.0f;	}			//if null set to 0
			float vol = oldVol + volVals.floorEntry(i).getValue() ;				//note's volume at this location
			maxNoteLvl = vol > maxNoteLvl ? vol : maxNoteLvl;
			noteHistogram.put(noteAbsLoc, vol);			
		}
	}//	addToRhythmHist
	
	//this will add the note's initial level only to the rhythm histogram
	private void addNoteStToRhythmHist(midiNoteData _note) {
		Float oldVol = noteHistogram.get(_note.stTime);				//existing volume at this location in song
		if(null == oldVol) {		oldVol = 0.0f;	}						//if null set to 0
		float vol = oldVol + _note.getNoteStVol() ;						//note's volume at this location
		maxNoteLvl = vol > maxNoteLvl ? vol : maxNoteLvl;
		noteHistogram.put(_note.stTime, vol);	
	}//	addToRhythmHist
	
	//finalize the processing of this song
	public void finalizeSong() {
		procDone = true;
		if(maxNoteLvl <= 1) {return;}//lvls in notes are ints, so <=1 means no notes in song
		//normalize all recorded note levels in note histogram - divide lvls by maxNoteLvl
		for(Long key : noteHistogram.keySet()) {noteHistogram.put(key,noteHistogram.get(key)/maxNoteLvl);}
		
	}
	
	//save all the song data in the appropriate format
	public void saveData() {//TODO
		
		
		
	}

}//myMidiSongData


//class to hold a single channel's worth of musical data in a song - 
//should include channel events and any relevant global meta events like tempo, key sig, etc.
//channels are atomic in that they define a single musical path, for instance a single instrument.
//multiple tracks may map to the same channel, or a single track may map to multiple channels. - different tracks may have different instrument names
//tracks are constructs to ease composition, while channels are constructs to ease performance
class myMidiChannel{
	//owning song of this channel
	public final myMidiSongData song;
	//channel index of this channel
	public final int chan;
	//ref to song state
	public songState state;
	
	//sorted by timestamp of note starting, and note stopping, for all notes in this channel
	private ConcurrentSkipListMap<Long,midiNoteData> notesOn, notesOff;
	//channel-specific events - program change - these are enabled until they are overwritten by other program changes
	private ConcurrentSkipListMap<Long,myChanEvt> progChange;
	//this might hold useful info about instrument(s) playing part
	//instrument name used in this channel - might change over time
	private ConcurrentSkipListMap<Long,String> chInstName;		
	
	public myMidiChannel(myMidiSongData _sng,songState _st, int _ch) {
		song=_sng; state=_st; chan=_ch;
		notesOn = new ConcurrentSkipListMap<Long,midiNoteData>();
		notesOff = new ConcurrentSkipListMap<Long,midiNoteData>();
		progChange = new ConcurrentSkipListMap<Long,myChanEvt>();
		chInstName = new ConcurrentSkipListMap<Long,String>();
	}

	
	//add note to structure
	public void addNote(midiNoteData _note) {
		//add note's volumes to rhythm histogram
		song.addToRhythmHist(_note);
		notesOn.put(_note.stTime, _note);
		notesOff.put(_note.endTime, _note);		
	}
	
	//check if note already exists in channel
	public boolean chanHasNote(int mididat1, Long endTime) {
		midiNoteData _note = notesOff.get(endTime);
		if(null==_note) {return false;}
		return (_note.midiData == mididat1);//if note has identical midi data and start time then note is in channel already		
	}
	
	//set/change instrument in this channel - uses general midi specification to describe instrument
	public void addProgramChange(long _t, byte[] _info) {
		int idx = (int)(_info[1]);
		progChange.put(_t, new myChanEvt(MidiCommand.ProgChange, myMidiSongData.GMPatches[idx], chan, _info));
	}

	public void setInstName(long _t, String _name) {
		String oldInst = chInstName.put(_t, _name);
//		if(oldInst != null) {
//			System.out.println("Remapping channel instrument name @ time " + _t);
//		}
	}
	
	
}//class myMidiChannel

//non-note channel events
class myChanEvt{
	public final String name;
	//non-note channel-specific command
	public final MidiCommand cmd;
	//channel
	public final int ch;
	//info bytes
	public final byte[] info;
	public myChanEvt(MidiCommand _cmd,String _name, int _ch,byte[] _info) {cmd=_cmd;name=_name;ch=_ch;info=_info;}
	
}//myChanEvt



//class to hold all data for a single track from a midi file - format this data to save as features
//tracks are mapped to 1 of 16 channels for playback. use channel mapping to correlate instrument - each channel will correspond to a single instrument 

class myMidiTrackData {
	//owning analyzer
	myMidiSongData song;
	//# of events, index in track listing of this track
	int numEvents, trIDX;
	//midi track that informs this object
	Track trk;
	//array of events in the track that informs this object
	MidiEvent[] events;
	
	//informational data to build features with
	//reference to all channels in song
	myMidiChannel[] chans;
	
	//channel this track is mapping to - if changes, need to monitor
	private ConcurrentSkipListMap<Long,Integer> curChanMap;
	
	private ConcurrentSkipListMap<Integer,Boolean> chansMapped;
	
	//these might hold useful info about instrument(s) playing part
	//title(s) of this track (might change over time) - might hold useful info about instrument playing part
	private ConcurrentSkipListMap<Long,String> trkTitle;
	//instrument name used in this track, per channel - might change over time
	private ConcurrentSkipListMap<Long,String>[] tInstName;	
	
	//ref to song state that holds all globally(across all channels)-applicable variables, such as tempo or key sig
	songState state;
	
	
	public myMidiTrackData(myMidiSongData _song, songState _st,  myMidiChannel[] _chns, Track _trk, int idx) {
		song=_song;
		state = _st;
		trk = _trk;
		numEvents = trk.size();
		events = new MidiEvent[numEvents];
		trIDX = idx;
		chans = _chns;
		curChanMap = new ConcurrentSkipListMap<Long,Integer>();
		//always assumes channel 0 is mapped
		curChanMap.put(0L, 0);		
		chansMapped = new ConcurrentSkipListMap<Integer,Boolean>();
		chansMapped.put(0, true);
		trkTitle = new ConcurrentSkipListMap<Long,String>();
		tInstName = new ConcurrentSkipListMap[16];
		for(int i=0;i<tInstName.length;++i) {
			tInstName[i]=new ConcurrentSkipListMap<Long,String>();
		}
	
	}
	
	//function processes all midi events 
	public void procEvents() {	
		//array that holds most recently turned-on note of a particular pitch
		midiNoteData[][] lastOnNote = new midiNoteData[16][128];
		for(int i=0;i<lastOnNote.length;++i) {
			lastOnNote[i] = new midiNoteData[128];
		}
		boolean hasChanEvt = false;
		for(int e=0;e<numEvents;++e) {
			MidiEvent ev = trk.get(e);
			events[e]=ev;
			//event has start time and message, in midi ticks
			long stTime = ev.getTick();
			MidiMessage msg = ev.getMessage();
			//message has length, message bytes array and status byte
			byte[] msgBytes = msg.getMessage();//(int)(byte & 0xFF)
			//build string of bytes
			String msgStr = ""+String.format("0x%02X", (int)(msgBytes[0]& 0xFF));
			for(int b=1;b<msgBytes.length;++b) {
				msgStr +=","+String.format("0x%02X", (int)(msgBytes[b]& 0xFF));
			}			
					
			//encodes command and channel - first byte of message
			int status = (int)(msgBytes[0] & 0xFF);//same as get status
			int command,chan;
			String str1 = "Note",
					str2 = "Vel";
			if(status < 0xF0) {//channel event - ignoring channel mode 
				hasChanEvt = true;
				//try {
				//most sig byte is command, least sig byte is chan
				int dat1 = (int)(msgBytes[1] & 0xFF), dat2;
				if(msgBytes.length > 2) {
					dat2 = (int)(msgBytes[2] & 0xFF);
				} else {
					dat2 = 0;
				}
				//Note on/off note value built from middle C == 60
				command = status & 0xF0;
				chan = status - command;
				//record channel that this track maps to, and time that this is set/changed - multiple events at sime timestamp might map to multiple channels
				//use curChanMap only for meta data settings, not here, where we're only recording channel events being sent along this channel
//				if((curChanMap.size() == 0) || (curChanMap.lastEntry().getValue() != chan)) {
//					curChanMap.put(stTime, chan);
//				}
				chansMapped.put(chan,true);
				
				MidiCommand cmd = MidiCommand.getVal(command);
				//check if enabling a note - sometimes note off == note on with dat2 (vel) == 0.  modify so that this is the case
				if((cmd == MidiCommand.NoteOn) || (cmd == MidiCommand.NoteOff)) {
//					nValType notev = nValType.getVal((dat1 % 12));
//					int octave = (dat1 / 12 -1);
//					if((3== song.mfa.thdIDX) && (trIDX == 4)) {
//						System.out.println("th : " + song.mfa.thdIDX + " | Track "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",status) + 
//						" channel : " + chan + " command : "+ cmd+" | " + str1 + " : " + dat1+" | note : " + notev  + " octave : " + octave + " | " + str2 + " : " +dat2+ " | bytes : [ "+msgStr + " ]");
//					}
					if((cmd == MidiCommand.NoteOff) || ((cmd == MidiCommand.NoteOn) && (dat2 == 0))) {//if turning off a note, or if noteOn + vol==0
						midiNoteData note = lastOnNote[chan][dat1];
						if(note == null) {//no note to turn off in array - happens because double notes at same timestamp, just ignore extra off command (errors in midi files)
//							if((1== song.mfa.thdIDX) && (trIDX == 3)) {
//								if (!chans[chan].chanHasNote(dat1, stTime)) {
//									System.out.println("th : " + song.mfa.thdIDX + " | Track "+trIDX+"!!!Ara is null for midi note : " + dat1 + " | chan : " + chan+"| end time : " +stTime + "| orig cmd : " + cmd +"| dat2 : " + dat2);													
//								}
//							}
						} else {//set note end time, add note to structures
							//turn off appropriate note, add note to permanent structures, remove from temp "on notes" structure
							note.setEndTime(stTime);
							chans[chan].addNote(note);
							lastOnNote[chan][dat1] = null;
//							if((1== song.mfa.thdIDX) && (trIDX == 3)) {
//								System.out.println("th : " + song.mfa.thdIDX + " | Track "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",status) + " | Note end : " + note.toString()+ "| orig cmd : " + cmd +"| dat2 : " + dat2);	
//							}
						}	
						cmd = MidiCommand.NoteOff;
						
						
					} else {//make new note, add to temp struct holding non-terminated notes
						//add note to temporary "on notes" queue
						if(lastOnNote[chan][dat1] != null) {//if not null then probably note sustaining with "expressive" change in volume 
							//or maybe previous note with same data was not turned off - probably happens because double notes (errors in midi files) - verify volume level and timestamp
							//discard same timestamp note as dupe
							//if different time stamp note then modify note to have new volume at this time stamp							
							midiNoteData otrNote = lastOnNote[chan][dat1];
							if(otrNote.stTime != stTime) {//if not equal then command is probably volume command for otrNote.  if vol different than last vol, save new volume of otrNote
								otrNote.setNoteVol(stTime, dat2);
//								//end current note, add new note
//								otrNote.setEndTime(stTime);
//								chans[chan].addNote(otrNote);
//								lastOnNote[chan][dat1] = note;
								//System.out.println("\nth : " + song.mfa.thdIDX + " | Track "+trIDX+" | Volume Mod of Note : " + otrNote.toString() + " from " + otrNote.getMostRecentVol() + " to " + dat2+"\n");							
							}
							//else start time same, note same, ignore dupe note
								
						} else {//						midiNoteData note = new midiNoteData(dat1, chan, dat2, stTime);
							//new note starting for this channel
							lastOnNote[chan][dat1] = new midiNoteData(dat1, chan, dat2, stTime);
						}						
//						System.out.println("Track "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",status) + 
//						" channel : " + chan + " command : "+ cmd+" | " + str1 + " : " + dat1+" | note : " + note  + " octave : " + octave + " | " + str2 + " : " +dat2+ " | bytes : [ "+msgStr + " ]");					
					}
				} else {
					switch(cmd) {
						case PolyKeyTouch : {
							break;}
						case CntrlChange : {
							//Set the controller Control_num on the given Channel to the specified Value. 
							//Control_num and Value must be in the inclusive range 0 to 127. The assignment 
							//of Control_num values to effects differs from instrument to instrument. The 
							//General MIDI specification defines the meaning of controllers 1 (modulation), 7 
							//(volume), 10 (pan), 11 (expression), and 64 (sustain), but not all instruments 
							//and patches respond to these controllers. Instruments which support those 
							//capabilities usually assign reverberation to controller 91 and chorus to controller 93. 
							
							//for our purposes we ignore controller change - this is for effects and so has little bearing on the actual content of the music							
							break;}
						case ProgChange : {
							//Switch the specified Channel to program (patch) Program_num, which must be between 0 and 127. 
							//The program or patch selects which instrument and associated settings that channel will 
							//emulate. The General MIDI specification provides a standard set of instruments, but 
							//synthesisers are free to implement other sets of instruments and many permit the user to 
							//create custom patches and assign them to program numbers. 

							//program change may hold relevant info pertaining to instrument - general midi spec describes type of instrument, 
							//so we want to manage this on a per channel basis
							
							chans[chan].addProgramChange(stTime, msgBytes);
							//System.out.println("Track "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",status) + 
							//		" channel : " + chan + " command : "+ cmd+" | " + str1 + " : " + dat1+" | " + str2 + " : " + dat2+" | Non-Note channel command | bytes : [ "+msgStr + " ]");
							break;}
						case ChanTouch : {
							//When a key is held down after being pressed, some synthesisers send the pressure, repeatedly 
							//if it varies, until the key is released, but do not distinguish pressure on different keys 
							//played simultaneously and held down. This is referred to as “monophonic” or “channel” aftertouch 
							//(the latter indicating it applies to the Channel as a whole, not individual note numbers on that channel). 
							//The pressure Value (0 to 127) is typically taken to apply to the last note played, but instruments 
							//are not guaranteed to behave in this manner. 							
							
							//for our purposes we ignore channel after touch
							break;}
						case PitchBend : {
							//Send a pitch bend command of the specified Value to the given Channel. The pitch bend Value 
							//is a 14 bit unsigned integer and hence must be in the inclusive range from 0 to 16383. 
							//The value 8192 indicates no pitch bend; 0 the lowest pitch bend, and 16383 the highest. 
							//The actual change in pitch these values produce is unspecified. 
							
							//for our purposes ignore we ignore pitch bend
							break;}
						default : {}
					}//switch on non-note channel commands					
					

				}//if nonNote channel commands	
				//}//try
//				catch (Exception _e) {
//					System.out.println("ch event exception : " +_e.getMessage());
//				}
				
			} else {		//sysex or file meta event - across all channels.  ignore sysex ?
				command = status;	
				MidiCommand cmd = MidiCommand.getVal(command);
				if(cmd == MidiCommand.FileMetaEvent) {
					procMetaEvents(msgBytes, command, stTime, msgStr);
				} else {//sysex - probably irrelevant for our purposes 
					//procSysex(stTime, msgBytes, msgStr);					
				}				
			}			
		}		
		//check if multiple channels in this track, or if no channels specified - shouldn't happen, but might
//		if(chansMapped.size() != 1) {
//			if(chansMapped.size() > 1) {//multiple channels are mapped by this track.
//				if(song.type==0) {//type 0 songs will have multiple channels in single track
//					
//				} else {
//					System.out.println("Multiple channels specified for track idx : " +trIDX+" of song : " + song.title + " # chans : " + chansMapped.size());									
//				}
//			} else if ((this.trIDX != 0) && (numEvents > 20)) {//no channels specified and more than 20 events in track data - track holds some small amount of metadata
//				System.out.println("No Chans specified for track idx : " +trIDX+" of song : " + song.title + " # chans : " + chansMapped.size()+ " has chan evt : "+ hasChanEvt+ " # ttl events :"+numEvents);						
//			}
//		}
	}//procEvents
	
	private void procSysex(long stTime, byte[] msgBytes, String msgStr) {
		//sysex - probably irrelevant for our purposes - send patch 
		//midi-specific format values are idx's 0 (0xF0), last byte (0xF7)
		//next bytes are manufacturer id  and  device id (aka channel) 
		//byte idx 1 is manufacturer id (0x41 is rolland, apparently), byte idx 2 is device id (or channel), where 7F means all channels
		//byte idx 1 is 0x7E for non-real time sysex msgs,
		//byte idx 1 is 0x7F for universal real time sysex msgs
		
		//int deviceID = msgBytes[2];
		String subMsg1 = "", subMsg2="";
		if(msgBytes[1] == 0x7e) {//non-realtime
			switch(msgBytes[3]) {
			case 0x00 : {			subMsg1 = "unused";		break;}
			case 0x01 : {			subMsg1 = "sample dump header";		break;}
			case 0x02 : {			subMsg1 = "sample data packet";		break;}
			case 0x03 : {			subMsg1 = "sample dump request";		break;}
			case 0x04 : {			subMsg1 = "midi time code";		break;}
			case 0x05 : {			subMsg1 = "sample dump extension";		break;}
			case 0x06 : {			subMsg1 = "general info";		break;}
			case 0x07 : {			subMsg1 = "file dump";		break;}
			case 0x08 : {			subMsg1 = "midi tuning standard (non-RT)";		break;}
			case 0x09 : {			subMsg1 = "***General Midi***";		
				switch (msgBytes[4]) {
				case 0x01 :{subMsg2 = "GM 1 Sys On";break;}
				case 0x02 :{subMsg2 = "GM Sys Off";break;}
				case 0x03 :{subMsg2 = "GM 2 Sys On";break;}
				}
			break;}
			case 0x0a : {			subMsg1 = "Downloadable Sounds";		break;}
			case 0x0b : {			subMsg1 = "File Ref Msg";		break;}
			case 0x0c : {			subMsg1 = "Midi Vis Cntrl";		break;}
			case 0x7b : {			subMsg1 = "EOF";		break;}
			case 0x7c : {			subMsg1 = "Wait";		break;}
			case 0x7d : {			subMsg1 = "Cancel";		break;}
			case 0x7e : {			subMsg1 = "NAK";		break;}
			case 0x7f : {			subMsg1 = "ACK";		break;}
			default : { subMsg1 = "unknown : "+ msgBytes[3];}
			}						

			//System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | SysEX Non-Realtime message : "+ subMsg1 +" | " +subMsg2 + " | bytes : [ "+msgStr + " ]");
		} else if(msgBytes[1] == 0x7f) {//realtime
			switch(msgBytes[3]) {
			case 0x00 : {			subMsg1 = "unused";		break;}
			case 0x01 : {			subMsg1 = "midi time code RT";		break;}
			case 0x02 : {			subMsg1 = "midi show control";		break;}
			case 0x03 : {			subMsg1 = "***Notation Info***"; 
				switch (msgBytes[4]) {
				case 0x01 :{subMsg2 = "Bar Number";break;}
				case 0x02 :{subMsg2 = "Time Sig : immediate";break;}
				case 0x42 :{subMsg2 = "Time Sig : delayed";break;}
				}
				
				break;}
			case 0x04 : {			subMsg1 = "device control";		break;}
			case 0x05 : {			subMsg1 = "Realtime MTC Cue";		break;}
			case 0x06 : {			subMsg1 = "MMC Cmd";		break;}
			case 0x07 : {			subMsg1 = "MMC Resp";		break;}
			case 0x08 : {			subMsg1 = "Midi Tuning Standard (Real Time)";		break;}
			case 0x09 : {			subMsg1 = "Controller Dest Setting (GM2)";		break;}
			case 0x0a : {			subMsg1 = "Key-based Inst Cntl";		break;}
			case 0x0b : {			subMsg1 = "Scalable Polyphony midi mip msg";		break;}
			case 0x0c : {			subMsg1 = "Mobile phone cntrl msg";		break;}
			default : { subMsg1 = "unknown : "+ msgBytes[3];}
			}						
			//System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | SysEX Realtime message : "+ subMsg1 +" | " +subMsg2 + " | bytes : [ "+msgStr + " ]");
		} else {//manufacturer specific						
			//System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | SysEX Manufacturer-Specific ID: " +  String.format("%02X ",msgBytes[1]) + " :bytes : [ "+msgStr + " ]");
		}
				
	}//procSysex


	//need to process bytes 2+ to find the length of the message
	private int getTrackLen(byte[] msgBytes) {	
		int idx = 2,len =  (int)(msgBytes[idx++] & 0xFF),ch;
		//if len value is greater than 127
		if (len >= 0x80) {//if greater than half-byte-max
			len &=0x7F;
			do {
				ch = (int)(msgBytes[idx++] & 0xFF);		
				len = (len << 7) | (ch & 0x7F);
			} while (ch >= 0x80);
		}
		return len;
	}//getTrackLen
	
			
	//idx 0 will be FF, idx 1 will be type of meta event,  idx 2+ will be relevant data
	//these will always span all channels
	//put all data in state
	private void procMetaEvents(byte[] msgBytes, int command, long stTime, String msgStr) {
		//used for string data encoded in midi msg
		int typeByte = (int)(msgBytes[1] & 0xFF);
		MidiMeta type = MidiMeta.getVal(typeByte);
		if(null==type) {
			//unknown midi meta events - some of the midi files access unknown meta events - codes 0x0C (12) , 0x1F(31), 0x62(98), 0x64(100), 0x6C(108)
			//assuming these meta events are irrelevant to our purposes so ignoring them
			System.out.println("Thd IDX : " + song.mfa.thdIDX + "\tType is seen as null (no MidiMeta obj defined for typeByte : " 
						+ typeByte + ", hex : " + String.format("%02X ", typeByte) +")" );
			//for(byte m : msgBytes) {
			//	System.out.println("\t Thd IDX : " + mfa.thdIDX + " | byte : " + String.format("%02X ", m));
			//}
		} else {//known midi meta event
			//message length is encoded in bytes 2+ - some messages can be very long, so may require more than 1 byte to encode
			int msgLength = getTrackLen(msgBytes);
			//idx 2 is start of message length byte(s)
			int msgStIdx = msgBytes.length - msgLength;
			String btsAsChar = song.buildStrFromByteAra(msgBytes, msgStIdx);
			switch(type) {
				case SeqNumber 		:{
					//This meta-event specifies a sequence Number between 0 and 65535, used to arrange multiple 
					//tracks in a type 2 MIDI file, or to identify the sequence in which a collection of type 0 
					//or 1 MIDI files should be played. The Sequence_number meta-event should occur at Time zero, 
					//at the start of the track. 
					btsAsChar = "";
					break;} 
				case Text 			:{
					//track 0 Texts are song-related,wheras other tracks might possibly have instrument related info
					//The Text specifies the title of the track or sequence. The first Title meta-event in a 
					//type 0 MIDI file, or in the first track of a type 1 file gives the name of the work. 
					//Subsequent Title meta-events in other tracks give the names of those tracks. 
					btsAsChar = "Text : " + btsAsChar;
					break;} 
				case Copyright 		:{
					//ignore for our purposes
					btsAsChar = "Copyright : " + btsAsChar;
					break;} 
				case TrackTitle 	:{				
					//track 0 track titles are song-related,wheras other tracks will possibly have instrument related info
					//The Text specifies the title of the track or sequence. The first Title meta-event in a 
					//type 0 MIDI file, or in the first track of a type 1 file gives the name of the work. 
					//Subsequent Title meta-events in other tracks give the names of those tracks. 
					
					btsAsChar = "Track Title : " + btsAsChar;
					String trkTtl = trkTitle.put(stTime, btsAsChar);
					if((null!= trkTtl) && !(trkTtl.trim().equals(btsAsChar.trim()))) {//build compound track title if title already exists
						trkTtl += "|"+btsAsChar;
						trkTitle.put(stTime,trkTtl);
					}
					break;} 
				case TrackInstName 	:{
					//The Text names the instrument intended to play the contents of this track, This is 
					//usually placed at time 0 of the track. Note that this meta-event is simply a description; 
					//MIDI synthesisers are not required (and rarely if ever) respond to it. This meta-event is 
					//particularly useful in sequences prepared for synthesisers which do not conform to the General 
					//MIDI patch set, as it documents the intended instrument for the track when the sequence is 
					//used on a synthesiser with a different patch set.
					//instrument name to use for current channel, current track
					
					btsAsChar = "Instrument name : " + btsAsChar;
					
					int curChan = curChanMap.lastEntry().getValue();
					chans[curChan].setInstName(stTime, btsAsChar);
					String trkInst = tInstName[curChan].put(stTime, btsAsChar);
					if((null!= trkInst) && !(trkInst.trim().equals(btsAsChar.trim()))) {
						System.out.println("Song : " + song.title + " | trkIDX :"+trIDX + " stTime : " + stTime + " | Remapping track instrument at this time from : "+ trkInst + " to " + btsAsChar);
					}
					break;} 
				case Lyric 			:{
					btsAsChar = "Lyric : " + btsAsChar;
					//System.out.println("Lyrics : " + btsAsChar);
					break;} 
				case Marker 		:{
					break;} 
				case CuePoint 		:{
					break;} 
				case ChPrefix 		:{
					//channel prefix :This meta-event specifies the MIDI channel that subsequent meta-events and System_exclusive events in this track
					//pertain to. The channel Number specifies a MIDI channel from 0 to 15. In fact, the Number may be as large as 255, 
					//but the consequences of specifying a channel number greater than 15 are undefined. 					
					int chan = ((int)(msgBytes[msgStIdx] & 0xFF)%16);
					btsAsChar = "trkIDX : " + trIDX + "| sttime : " + stTime + " : Channel Change to Ch:"+chan+ ": all subsequent meta events go to this channel.";
					Integer oldChan = curChanMap.put(stTime, chan);
//					if ((null != oldChan) && (trIDX != 0)) {
//						System.out.println("Song : " + song.title + " | remapping channel : "+ btsAsChar);
//					}
					chansMapped.put(chan,true);
					//always only happens at beginning of track -  tracks never remap to other channels in any current midi file
					//if((song.type == 1) && (trIDX == 0)) {//never happens - type 1 songs have multiple tracks, so trk 0 will never have channel-specific msgs
					//if((song.type == 0) && (curChan.size() > 1)) {//never happens - type 0 songs only have 1 track - need to record if track 0 sets up specifics for multiple channel mappings
						//System.out.println("Song Midi type : "+ song.type +" | Meta event : "+btsAsChar);
					//}
					break;} 
				case Port 			:{
					//This meta-event specifies that subsequent events in the Track should be sent to MIDI port (bus) 
					//Number, between 0 and 255. This meta-event usually appears at the start of a track with Time zero, 
					//but may appear within a track should the need arise to change the port while the track is being played. 					
					break;} 
				case EndTrack 		:{
					//An End_track marks the end of events for the specified Track. The Time field gives the 
					//total duration of the track, which will be identical to the Time in the last event before the End_track. 
					btsAsChar = "End Track";
					break;} 
				
				case SetTempo 		:{
//					//The tempo is specified as the Number of microseconds per quarter note, between 1 and 16777215. A value of
//					//corresponds to 120 bpm. To convert beats per minute to a Tempo value, divide 60,000,000 by the beats per minute. 
					int val = (((int)(msgBytes[msgStIdx] & 0xFF) << 16) | ((int)(msgBytes[msgStIdx+1] & 0xFF) << 8) | (int)((msgBytes[msgStIdx+2] & 0xFF)));
					btsAsChar = "# of micros : " + val + " == " + (60000000.0f/val) + " BPM";
					state.setTempo(stTime, msgBytes, msgStIdx);
					break;} 
				case SMPTEOffset 	:{
					//This meta-event, which must occur with a zero Time at the start of a track, 
					//specifies the SMPTE time code at which it should start playing. The FracFrame field gives the fractional frame time (0 to 99). 
					btsAsChar = "SMPTE Offset Hr:" + (int)(msgBytes[msgStIdx] & 0xFF)+"|Min:"+ (int)(msgBytes[msgStIdx+1] & 0xFF)+"|Sec:"+ (int)(msgBytes[msgStIdx+2] & 0xFF)+"|Fr:"+ (int)(msgBytes[msgStIdx+3] & 0xFF)+":"+ (int)(msgBytes[msgStIdx+4] & 0xFF);
					break;} 
				case TimeSig 		:{//Num, Denom, Click, NotesQ
//	//				The time signature, metronome click rate, and number of 32nd notes per MIDI quarter note (24 MIDI clock ticks) 
//	//				are given by the numeric arguments. Num gives the numerator of the time signature as specified on sheet music. 
//	//				Denom specifies the denominator as a negative power of two, for example 2 for a quarter note, 3 for an eighth note, etc. 
//	//				Click gives the number of MIDI clocks per metronome click, and NotesQ the number of 32nd notes in the nominal MIDI quarter 
//	//				note time of 24 clocks (8 for the default MIDI quarter note definition).
					int num = (int)(msgBytes[msgStIdx] & 0xFF);
					nDurType denom = nDurType.getVal((int)(msgBytes[msgStIdx+1] & 0xFF));
					int click = (int)(msgBytes[msgStIdx+2] & 0xFF);
					int noteQ = (int)(msgBytes[msgStIdx+3] & 0xFF);
					btsAsChar = "Time Sig :  " + num + " beats per measure, " + denom + " gets the beat. Click : " + click + " midi clocks per click; and " + noteQ + " 32nd notes per MIDI qtr note time (24 clocks)";
//					
					state.setTimeSig(stTime, msgBytes, msgStIdx);
					break;} 
				case KeySig 		:{
//	//				The key signature is specified by the numeric Key value, which is 0 for the key of C, a positive value for each sharp above C, 
//	//				or a negative value for each flat below C, thus in the inclusive range -7 to 7. The Major/Minor field is a quoted string which 
//	//				will be major for a major key and minor for a minor key. For our purposes the "major" or "minor" tag is irrelevant - only # of 
//	//				sharps/flats matters, since the actual scales remain the same.
//					//System.out.println("Calculating Key Sig : idx : " + msgStIdx + " len of msgBytes : " + msgBytes.length);
					int ksByteVal = 0;
					try {
						//keep keysig between -7 and 7 - if more than 7 sharps or 7 flats then this is non-standard, following code should address - force -7 to 5 and -6 to 6, which are enharmonic
						ksByteVal = (((((int)(msgBytes[msgStIdx])) +17) % 12)-5);
					}
					catch(Exception e) {//error in encoded key sig - assume c maj
						ksByteVal = 0;
					}
					keySigVals keySigVal = keySigVals.getVal(ksByteVal);
//						if(keySigVal == null) {
//							System.out.println("Null Keysig val | " + (int)(msgBytes[msgStIdx]));						
//						}
					btsAsChar = "Key Sig :  " + keySigVal;		
					state.setKeySig(stTime, msgBytes, msgStIdx);
					break;} 
				case SeqSpecific 	:{
					//The Sequencer_specific meta-event is used to store vendor-proprietary data in a MIDI file. 
					//The Length can be any value between 0 and 2^28 - 1, specifying the number of Data bytes (between 0 and 255) which follow. 
					//Sequencer_specific records may be very long; programs which process MIDI CSV files should be careful to protect against buffer overflows and truncation of these records. 
					break;} 
				default  :{	}						
			}//switch	
		if((trIDX==0) && (song.title.contains("K622 Clarinet Concerto"))) {
			System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",command) + 
			" command : "+ MidiCommand.FileMetaEvent+" | type : " +String.format("0x%02X",typeByte) + " | type : " +type + " | bytes : [ "+msgStr + " ] | msg Length : " + msgLength + " | msg as text/data : "+ btsAsChar);
			
		}
			//if (trIDX!=0) {
//				if (msgLength >= 16) {
//					System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",command) + 
//						" command : "+ MidiCommand.FileMetaEvent+" | type : " +String.format("0x%02X",typeByte) + " | type : " +type + " | msg Length : " + msgLength + " | msg as text/data : "+ btsAsChar);
//				} else {
//					System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",command) + 
//					" command : "+ MidiCommand.FileMetaEvent+" | type : " +String.format("0x%02X",typeByte) + " | type : " +type + " | bytes : [ "+msgStr + " ] | msg Length : " + msgLength + " | msg as text/data : "+ btsAsChar);
//					
//				}
			//} 
		}
	}//procMetaEvents
	
	public String getStringRep() {
		String res = "";
		return res;
	}
		
		
}//class trackData


//this class will manage the state of the song at any particular instant of time.  
//this state will be shared across all channels, and should hold information like key sig, tempo, time sig, etc.
//in other words, all values that will affect all channels.  only 1 songstate for any song
//indexed by start time
class songState {
	//owning song for the song state
	public final myMidiSongData song;
	//refs to all channels in this song
	public myMidiChannel[] midiChans;
	//global variables indexed by start time
	//song total length
	private final long songLen;
	
	//time sig map of this song
	private ConcurrentSkipListMap<Long,timeSig> timeSigMap;
	//metronome click rate map of this song - the number of MIDI clocks per metronome click
	private ConcurrentSkipListMap<Long,Integer> nomeClickMap;
	//gives # of 32 notes in the nominal MIDI quarter note time of 24 clicks (8 for default midi qtr note def)
	private ConcurrentSkipListMap<Long,Integer> thrtyScndNotesPerMidiQtr;
	
	//key sig map of this song
	private ConcurrentSkipListMap<Long, keySigVals> keySigMap;
	
	//tempo map of this song
	private ConcurrentSkipListMap<Long,Float> tempoMap;
	
	
	public songState(myMidiSongData _song, myMidiChannel[] _mc) {
		song=_song;
		midiChans = _mc;
		songLen = song.tickLen;
		timeSigMap = new ConcurrentSkipListMap<Long,timeSig>();
		nomeClickMap = new ConcurrentSkipListMap<Long, Integer>();
		thrtyScndNotesPerMidiQtr = new ConcurrentSkipListMap<Long,Integer>();	
		tempoMap = new ConcurrentSkipListMap<Long,Float>();
		keySigMap = new ConcurrentSkipListMap<Long, keySigVals>();
	}//ctor
	
	//set global time signature
	public void setTimeSig(long stTime, byte[] msgBytes, int msgStIdx) {
//		The time signature, metronome click rate, and number of 32nd notes per MIDI quarter note (24 MIDI clock ticks) 
//		are given by the numeric arguments. Num gives the numerator of the time signature as specified on sheet music. 
//		Denom specifies the denominator as a negative power of two, for example 2 for a quarter note, 3 for an eighth note, etc. 
//		Click gives the number of MIDI clocks per metronome click, and NotesQ the number of 32nd notes in the nominal MIDI quarter 
//		note time of 24 clocks (8 for the default MIDI quarter note definition).
		timeSigMap.put(stTime, new timeSig((int)(msgBytes[msgStIdx] & 0xFF), nDurType.getVal((int)(msgBytes[msgStIdx+1] & 0xFF))));
		nomeClickMap.put(stTime, (int)(msgBytes[msgStIdx+2] & 0xFF));//Click gives the number of MIDI clocks per metronome click
		thrtyScndNotesPerMidiQtr.put(stTime, (int)(msgBytes[msgStIdx+3] & 0xFF));
		
	}//setTimeSig
	
	public void setKeySig(long stTime, byte[] msgBytes, int msgStIdx) {
//		The key signature is specified by the numeric Key value, which is 0 for the key of C, a positive value for each sharp above C, 
//		or a negative value for each flat below C, thus in the inclusive range -7 to 7. The Major/Minor field is a quoted string which 
//		will be major for a major key and minor for a minor key. For our purposes the "major" or "minor" tag is irrelevant - only # of 
//		sharps/flats matters, since the actual scales remain the same.
		int ksByteVal = 0;
		try {//keep keysig between -7 and 7 - if more than 7 sharps or 7 flats then this is non-standard, following code should address - force -7 to 5 and -6 to 6, which are enharmonic
			ksByteVal = (((((int)(msgBytes[msgStIdx])) +17) % 12)-5);
		}
		catch(Exception e) {ksByteVal = 0;}//error in encoded key sig - assume c maj
		keySigMap.put(stTime, keySigVals.getVal(ksByteVal));		
	}//setKeySig
	
	public void setTempo(long stTime, byte[] msgBytes, int msgStIdx) {
		//The tempo is specified as the Number of microseconds per quarter note, between 1 and 16777215. A value of
		//corresponds to 120 bpm. To convert beats per minute to a Tempo value, divide 60,000,000 by the beats per minute. 
		int val = (((int)(msgBytes[msgStIdx] & 0xFF) << 16) | ((int)(msgBytes[msgStIdx+1] & 0xFF) << 8) | (int)((msgBytes[msgStIdx+2] & 0xFF)));
		float tmpo = (60000000.0f/val);//in beats per min
		tempoMap.put(stTime, tmpo);

	}//setTempo
	
	
	
	
}//class songState

//a class that holds a time signature
class timeSig {
	public final int num;
	public final nDurType denom;
	timeSig(int _num, nDurType _dnm){num=_num;denom=_dnm;}	
}



//object to hold midi note data.  is comparable for keying notes by start time
class midiNoteData implements Comparable<midiNoteData>{
	public final long stTime;
	public long endTime;
	public final int octave, midiData;
	public final int channel;
	public final nValType note;
	
	//map of volume for notes - "expressive" notes change volume through explicity midi commands
	//key is offset from start of note
	private ConcurrentSkipListMap<Integer, Integer> noteVol;
	//the max volume of this note, and the time relative to the start of the note when it happens
	private int maxVol, maxVolRelTime;
	public int noteDur;
	
	//_mDat is midi note value, _vol is midi volume.  
	public midiNoteData(int _mDat,int _chan, int _vol, long _stTime) {
		midiData=_mDat;channel=_chan;note = nValType.getVal((midiData % 12));octave = midiData / 12 -1;stTime=_stTime;
		noteVol = new ConcurrentSkipListMap<Integer, Integer>();
		noteDur = 0;
		noteVol.put(0,_vol);
		maxVol = _vol;
		maxVolRelTime = 0;
	}
	
	public void setEndTime(long _endTime) {endTime = _endTime; noteDur = (int)(endTime-stTime);}	
	public long getEndTime() {return endTime;}
	//get record of volume changes for note
	public void setNoteVol(long _absTime, int _vol) {
		int relTime = (int)(_absTime - stTime);
		noteVol.put(relTime, _vol);
		if(_vol > maxVol) {
			maxVol = _vol;
			maxVolRelTime = relTime;
		}
	}
	//get volume of note when note starts
	public int getNoteStVol() {	return noteVol.get(0);}
	
	public ConcurrentSkipListMap<Integer, Integer> getAllNoteVols(){
		return noteVol;
	}
	
	//get relative time and value of note's max volume
	//idx 0 : relative time from beginning of note
	//idx 1 : max volume
	public int[] getNoteMaxVol() {	return new int[] {maxVolRelTime, maxVol};}
	public int getMostRecentVol() {return noteVol.lastEntry().getValue();}
	
	@Override
	public int compareTo(midiNoteData otr) {//sort first by start time, then note value, if they start at the same time
		if(this.stTime==otr.stTime) {
			if(this.midiData==otr.midiData) {return 0;}else {return (this.midiData > otr.midiData)? 1 : -1;}}
		return (this.stTime > otr.stTime)? 1 : -1;
	}//compareTo
	
	
	
	public String toString() {
		String res = "Note : "+ note + " | Octave : "+ octave +" | Chan : " + channel + " | StTime:"+stTime+" | Endtime : " + endTime +" | midiNote : " + midiData; 
		return res;
	}

}//midiNoteData

