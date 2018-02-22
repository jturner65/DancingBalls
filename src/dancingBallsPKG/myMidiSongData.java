package dancingBallsPKG;

import java.util.concurrent.*;

import javax.sound.midi.*;

public class myMidiSongData {	
	public myMidiFileAnalyzer mfa;
	//length of sequence in time
	public final long tickLen;
	//length of sequence data in bytes, 
	public final int byteLen;
	//type of midi file
	public final int type;

	//composer of this track, based on file listing
	public final String composer;
	//title
	public final String title;
	//all trackdata objects for this song
	public myMidiTrackData[] midiTracks;
	
	//want to store all data per channel
	public myMidiChannel[] midiChans;
	
	//song-wide variables - key sig, time sig, tempo, etc
	
	public myMidiSongData (myMidiFileAnalyzer _mfa, MidiFileFormat _fmt, Sequence _seq) {
		mfa=_mfa;
		byteLen = _fmt.getByteLength();
		tickLen = _seq.getTickLength();
		//float divType = format.getDivisionType();
		//all files are divType 0.0 -> PPQ: The tempo-based timing type, for which the resolution is expressed in pulses (ticks) per quarter note
		type = _fmt.getType();
		composer = mfa.fileListing.composerName;
		title =  mfa.fileListing.dispName;
		//if type == 0 then only 1 track
		Track[] tracks = _seq.getTracks();
		int numTracks = tracks.length;	
		midiTracks = new myMidiTrackData[numTracks];
		
		midiChans = new myMidiChannel[16];//never more than 16 channels
		for(int i=0; i<midiChans.length;++i) {	midiChans[i] = new myMidiChannel(this, i);	}		
//		if(3== mfa.thdIDX) {
//		System.out.println("Thd IDX : " + mfa.thdIDX + " | Composer : " +composer + "\tSong Name : " + title +"\tFormat info : byte len : " + byteLen+" | midi type : " + type + " | # of tracks  : "+ numTracks);
//		}
		for(int i=0;i<numTracks;++i) {
			midiTracks[i] = new myMidiTrackData(this, midiChans, tracks[i], i);
		}
	
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

}//myMidiSongData


//class to hold a single channel's worth of musical data in a song - should include channel events
class myMidiChannel{
	//owning song of this channel
	public final myMidiSongData song;
	//channel index of this channel
	public final int chan;
	
	//sorted by timestamp of note starting, and note stopping, for all notes in this channel
	private ConcurrentSkipListMap<Long,midiNoteData> notesOn, notesOff;
	//channel-specific events - program change - these are enabled until they are overwritten
	private ConcurrentSkipListMap<Long,myChanEvt> progChange;
	
	//title(s) of this channel (might change)
	private ConcurrentSkipListMap<Long,String> chTitle;
	//instrument
	
	public myMidiChannel(myMidiSongData _sng, int _ch) {
		song=_sng; chan=_ch;
		notesOn = new ConcurrentSkipListMap<Long,midiNoteData>();
		notesOff = new ConcurrentSkipListMap<Long,midiNoteData>();
		progChange = new ConcurrentSkipListMap<Long,myChanEvt>();
		chTitle = new ConcurrentSkipListMap<Long,String>();
	}

	
	//add note to structure
	public void addNote(midiNoteData _note) {
		notesOn.put(_note.stTime, _note);
		notesOff.put(_note.endTime, _note);		
	}
	//set/change instrument in this channel - uses general midi specification to describe instrument
	public void addProgramChange(long _t, byte[] _info) {
		progChange.put(_t, new myChanEvt(MidiCommand.ProgChange, chan, _info));
	}
	
	public void setTitle(long _t, String _ttl) {chTitle.put(_t, _ttl);}
	
	
}//class myMidiChannel

//non-note channel events
class myChanEvt{
	//non-note channel-specific command
	public final MidiCommand cmd;
	//channel
	public final int ch;
	//info bytes
	public final byte[] info;
	public myChanEvt(MidiCommand _cmd,int _ch,byte[] _info) {cmd=_cmd;ch=_ch;info=_info;}
	
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
	
	public myMidiTrackData(myMidiSongData _song, myMidiChannel[] _chns, Track _trk, int i) {
		song=_song;
		trk = _trk;
		numEvents = trk.size();
		events = new MidiEvent[numEvents];
		trIDX = i;
		chans = _chns;			
		procEvents();
	}
	
	private void procEvents() {	
		//array that holds most recently turned-on note of a particular pitch
		midiNoteData[][] lastOnNote = new midiNoteData[16][128];
		for(int i=0;i<lastOnNote.length;++i) {
			lastOnNote[i] = new midiNoteData[128];
		}
		
		
		for(int e=0;e<numEvents;++e) {
			MidiEvent ev = trk.get(e);
			events[e]=ev;
			//event has start time and message
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
						cmd = MidiCommand.NoteOff;
						midiNoteData note = lastOnNote[chan][dat1];
						if(note == null) {//no note to turn off in array - happens because double notes at same timestamp, just ignore extra off command (errors in midi files)
//							if((3== song.mfa.thdIDX) && (trIDX == 4)) {
//								System.out.println("\nth : " + song.mfa.thdIDX + " | Track "+trIDX+"!!!!!!!!!!!Note in array structure is null for midi note : " + dat1 + " | Note : "+ notev + " | Octave : " + octave+ " | chan : " + chan+"\n");													
//							}
						} else {//set note end time, add note to structures
							//turn off appropriate note, add note to permanent structures, remove from temp "on notes" structure
							note.setEndTime(stTime);
							chans[chan].addNote(note);
							lastOnNote[chan][dat1] = null;
//							System.out.println("Track "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",status) + 
//							" channel : " + chan + " command : "+ cmd+" | " + str1 + " : " + dat1+" | note : " + note  + " octave : " + octave + " | " + str2 + " : " +dat2+ " | bytes : [ "+msgStr + " ]");
						}

						
					} else {//make new note, add to temp struct holding non-terminated notes
						midiNoteData note = new midiNoteData(dat1, chan, dat2, stTime);
						//add note to temporary "on notes" queue
						if(lastOnNote[chan][dat1] != null) {//if not null then previous note with same data was not turned off - probably happens because double notes (errors in midi files) - verify timestamp
							midiNoteData otrNote = lastOnNote[chan][dat1];
							if(otrNote.stTime != note.stTime) {//if not equal then double notes are in sequence-  turn off old note, add new one
								//end current note, add new note
								otrNote.setEndTime(stTime);
								chans[chan].addNote(otrNote);
								lastOnNote[chan][dat1] = note;
								//System.out.println("\nth : " + song.mfa.thdIDX + " | Track "+trIDX+"!!!!!!!!!!!Note : " + note.toString() + "\ncollided in lastOnNote array with note " + lastOnNote[chan][dat1].toString()+"\n");							
							}
						} else {
							lastOnNote[chan][dat1] = note;
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
							System.out.println("Track "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",status) + 
									" channel : " + chan + " command : "+ cmd+" | " + str1 + " : " + dat1+" | " + str2 + " : " + dat2+" | Non-Note channel command | bytes : [ "+msgStr + " ]");
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
				
			} else {		//sysex or file meta event
				command = status;	
				MidiCommand cmd = MidiCommand.getVal(command);
				if(cmd == MidiCommand.FileMetaEvent) {
					procMetaEvents(msgBytes, command, stTime, msgStr);
				} else {//sysex
					//System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | SysEX status : "+ String.format("0x%02X",status) + " Command : "+ cmd + " | bytes : [ "+msgStr + " ]");
				}				
			}
			
		}		
	}//procEvents


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
	private void procMetaEvents(byte[] msgBytes, int command, long stTime, String msgStr) {
		//used for string data encoded in midi msg
		int typeByte = (int)(msgBytes[1] & 0xFF);
		MidiMeta type = MidiMeta.getVal(typeByte);
		if(null==type) {
			//unknown midi meta events - some of the midi files access unknown meta events - codes 0x0C (12) , 0x1F(31), 0x62(98), 0x64(100), 0x6C(108)
			//assuming these meta events are irrelevant to our purposes so ignoring them
			System.out.println("Thd IDX : " + song.mfa.thdIDX + "\tType : " + type + " is seen as null.  TypeByte is : " + typeByte + " | " + String.format("%02X ", typeByte) );
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
					break;} 
				case TrackInstName 	:{
					//The Text names the instrument intended to play the contents of this track, This is 
					//usually placed at time 0 of the track. Note that this meta-event is simply a description; 
					//MIDI synthesisers are not required (and rarely if ever) respond to it. This meta-event is 
					//particularly useful in sequences prepared for synthesisers which do not conform to the General 
					//MIDI patch set, as it documents the intended instrument for the track when the sequence is 
					//used on a synthesiser with a different patch set. 
					btsAsChar = "Instrument name : " + btsAsChar;
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
					//channel prefix :This meta-event specifies the MIDI channel that subsequent meta-events and System_exclusive events 
					//pertain to. The channel Number specifies a MIDI channel from 0 to 15. In fact, the Number may be as large as 255, 
					//but the consequences of specifying a channel number greater than 15 are undefined. 					
					int val = ((int)(msgBytes[msgStIdx] & 0xFF)%16);
					btsAsChar = "Channel Change to Ch:"+val;
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
					//The tempo is specified as the Number of microseconds per quarter note, between 1 and 16777215. A value of
					//corresponds to 120 bpm. To convert beats per minute to a Tempo value, divide 60,000,000 by the beats per minute. 
					int val = (((int)(msgBytes[msgStIdx] & 0xFF) << 16) | ((int)(msgBytes[msgStIdx+1] & 0xFF) << 8) | (int)((msgBytes[msgStIdx+2] & 0xFF)));
					btsAsChar = "# of micros : " + val + " == " + (60000000.0f/val) + " BPM";
					break;} 
				case SMPTEOffset 	:{
					//This meta-event, which must occur with a zero Time at the start of a track, 
					//specifies the SMPTE time code at which it should start playing. The FracFrame field gives the fractional frame time (0 to 99). 
					btsAsChar = "SMPTE Offset Hr:" + (int)(msgBytes[msgStIdx] & 0xFF)+"|Min:"+ (int)(msgBytes[msgStIdx+1] & 0xFF)+"|Sec:"+ (int)(msgBytes[msgStIdx+2] & 0xFF)+"|Fr:"+ (int)(msgBytes[msgStIdx+3] & 0xFF)+":"+ (int)(msgBytes[msgStIdx+4] & 0xFF);
					break;} 
				case TimeSig 		:{//Num, Denom, Click, NotesQ
	//				The time signature, metronome click rate, and number of 32nd notes per MIDI quarter note (24 MIDI clock times) 
	//				are given by the numeric arguments. Num gives the numerator of the time signature as specified on sheet music. 
	//				Denom specifies the denominator as a negative power of two, for example 2 for a quarter note, 3 for an eighth note, etc. 
	//				Click gives the number of MIDI clocks per metronome click, and NotesQ the number of 32nd notes in the nominal MIDI quarter 
	//				note time of 24 clocks (8 for the default MIDI quarter note definition).
					int num = (int)(msgBytes[msgStIdx] & 0xFF);
					nDurType denom = nDurType.getVal((int)(msgBytes[msgStIdx+1] & 0xFF));
					int click = (int)(msgBytes[msgStIdx+2] & 0xFF);
					int noteQ = (int)(msgBytes[msgStIdx+3] & 0xFF);
					btsAsChar = "Time Sig :  " + num + " beats per measure, " + denom + " gets the beat. Click : " + click + " midi clocks per click; and " + noteQ + " 32nd notes per MIDI qtr note time (24 clocks)";
					
					break;} 
				case KeySig 		:{
	//				The key signature is specified by the numeric Key value, which is 0 for the key of C, a positive value for each sharp above C, 
	//				or a negative value for each flat below C, thus in the inclusive range -7 to 7. The Major/Minor field is a quoted string which 
	//				will be major for a major key and minor for a minor key. For our purposes the "major" or "minor" tag is irrelevant - only # of 
	//				sharps/flats matters, since the actual scales remain the same.
					//System.out.println("Calculating Key Sig : idx : " + msgStIdx + " len of msgBytes : " + msgBytes.length);
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
					btsAsChar = "Key Sig :  " + keySigVal;					break;} 
				case SeqSpecific 	:{
					//The Sequencer_specific meta-event is used to store vendor-proprietary data in a MIDI file. 
					//The Length can be any value between 0 and 2^28 - 1, specifying the number of Data bytes (between 0 and 255) which follow. 
					//Sequencer_specific records may be very long; programs which process MIDI CSV files should be careful to protect against buffer overflows and truncation of these records. 
					break;} 
				default  :{	}						
			}//switch	
		
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
//			if ((msgLength >= 16) && (type == MidiMeta.Text) && (trIDX!=0)) {System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",command) + 
//					" command : "+ MidiCommand.FileMetaEvent+" | type : " +String.format("0x%02X",typeByte) + " | type : " +type + " | msg Length : " + msgLength + " | msg as text/data : "+ btsAsChar);
//			} 
//			else {System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",command) + 
//					" command : "+ MidiCommand.FileMetaEvent+" | type : " +String.format("0x%02X",typeByte) + " | type : " +type + " | bytes : [ "+msgStr + " ] | msg Length : " + msgLength + " | msg as text/data : "+ btsAsChar);
//			}

		}
	}//procMetaEvents
	
	public String getStringRep() {
		String res = "";
		return res;
	}
		
		
}//class trackData

//object to hold midi note data.  is comparable for keying notes by start time
class midiNoteData implements Comparable<midiNoteData>{
	public final long stTime;
	public long endTime;
	public final int octave, midiData, vol;
	public final int channel;
	public final nValType note;
	
	public midiNoteData(int _mDat,int _chan, int _vol, long _stTime) {midiData=_mDat;channel=_chan;vol=_vol;note = nValType.getVal((midiData % 12));octave = midiData / 12 -1;stTime=_stTime;}
	public void setEndTime(long _endTime) {endTime = _endTime;}	
	public long getEndTime() {return endTime;}
	
	@Override
	public int compareTo(midiNoteData otr) {//sort first by start time, then note value, if they start at the same time
		if(this.stTime==otr.stTime) {
			if(this.midiData==otr.midiData) {return 0;}else {return (this.midiData > otr.midiData)? 1 : -1;}}
		return (this.stTime > otr.stTime)? 1 : -1;
	}//compareTo
	
	public String toString() {
		String res = "Note : "+ note + " | Octave : "+ octave +" | Chan : " + channel + " | StTime:"+stTime+" | Endtime : " + endTime +" | Vol : "+vol+" | midiNote : " + midiData; 
		return res;
	}

}//midiNoteData

