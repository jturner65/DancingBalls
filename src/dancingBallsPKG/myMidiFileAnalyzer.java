package dancingBallsPKG;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;

import javax.sound.midi.*;

//a class that provides functionality to load and analyze a midi file, and save analysis data
public class myMidiFileAnalyzer {
	myAudioManager mgr;
	//ref to info about file from audioFileManager
	public AudioFile fileListing;
	public Sequence sequence;
	public MidiFileFormat format;
	public int byteLength, type, numTracks;
	public Long tickLen;
	public String composer;
	
	public boolean loadedSeq;
	
	public int midiFileType; //either 0, 1, or 2
		
	public myMidiFileAnalyzer(myAudioManager _mgr,AudioFile _fileListing) {
		mgr = _mgr;
		fileListing = _fileListing;
		composer = fileListing.composerDir.dispName;
		loadedSeq = false;
	}

	public boolean loadAudio() {
		try{		    //load sequence
			File f = fileListing.filePath.toFile();
		    if(f==null) {
		    	mgr.pa.outStr2Scr("Null File obj : file not found : " + fileListing.filePath);
		    	return false;
		    }
		    format = MidiSystem.getMidiFileFormat(f);
		    sequence = MidiSystem.getSequence(f);
		    loadedSeq = true;
		}
		catch( InvalidMidiDataException ex ){
			//mgr.pa.outStr2Scr( "This file is not a valid midi file : "+ fileListing.filePath+":\t"+ex.getMessage() );
			return false;}
		catch( IOException ex ) { 
			//mgr.pa.outStr2Scr( "Had a problem accessing this midi file : "+ fileListing.filePath+":\t"+ex.getMessage() );
			return false;}
		return true;
	}//loadAudio
	
	//analyze file for various data points
	//get property key values
//	"author" String name of the author of this file 
//	"title" String title of this file 
//	"copyright" String copyright message 
//	"date" Date date of the recording or release 
//	"comment" 

	//format of saved feature : 
	//integer encoding of composer
	//encoding of track #
	//
	//sequence-string (10 element float 1-hot) of location of measure within sequence
	//sequence-string of location of note transition within measure
	//note value and duration
	//prev note value and duration
	//next note value and duration
	
	//have everything in ticks - maybe should use location in ticks to define training examples
	
	public void analyze() {
		if(!loadedSeq) {return;}
		//analyze sequence for midi data
		byteLength = format.getByteLength();
		tickLen = sequence.getTickLength();
		//float divType = format.getDivisionType();
		//all files are divType 0.0 -> PPQ: The tempo-based timing type, for which the resolution is expressed in pulses (ticks) per quarter note
		type = format.getType();
		//if type == 0 then only 1 track
		Track[] tracks = sequence.getTracks();
		numTracks = tracks.length;
		if((fileListing.dispName.equals("hyme")) ||
				(fileListing.dispName.equals("Symphony n40 K550 1mov")))	{
			System.out.println("Composer : " +composer + "\tSong Name : " + fileListing.dispName +"\tFormat info : byte len : " + byteLength+" | midi type : " + type + " | # of tracks  : "+ numTracks);
			for(int i=0;i<tracks.length;++i) {
				trackData tmpTrk = new trackData(this, tracks[i], i);
			}
			System.out.println("");
		}
		//read midi message data in for each track
		
		//may be short message (all but sys-ex and meta data like key)
		// ShortMessage: http://docs.oracle.com/javase/8/docs/api/javax/sound/midi/ShortMessage.html
		// what Data1 and Data2 will be, refer to the midi spec: http://www.midi.org/techspecs/midimessages.php

//		for(int i=0;i<tracks.length;++i) {
//			trackData tmpTrk = new trackData(this, tracks[i], i);
//			
//		}
		
		
	
		
	}//analyze
	
	//used to build message from bytes in midi messages
	public String buildStrFromByteAra(byte[] msgBytes, int stIdx) {
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
	
	
}//myMidiFileAnalyzer

//class to hold all data for a single track from a midi file - format this data to save as features
//tracks are mapped to 1 of 16 channels for playback.  
class trackData{
	int numEvents, trIDX;
	Track trk;
	MidiEvent[] events;
	myMidiFileAnalyzer fan;
	
	public trackData(myMidiFileAnalyzer _fan, Track _trk, int i) {
		fan=_fan;
		trk = _trk;
		numEvents = trk.size();
		events = new MidiEvent[numEvents];
		trIDX = i;
		procEvents();
	}
	
	private void procEvents() {		
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
			int dat1,dat2,command,chan;
			String str1 = "Note",
					str2 = "Vel";
			if(status < 0xF0) {//channel event - ignoring channel mode 
				//most sig byte is command, least sig byte is chan
				dat1 = (int)(msgBytes[1] & 0xFF);
				if(msgBytes.length > 2) {
					dat2 = (int)(msgBytes[2] & 0xFF);
				} else {
					dat2 = 0;
				}
				command = status & 0xF0;
				chan = status - command;
				MidiCommand cmd = MidiCommand.getVal(command);
				//sometimes note off == note on with dat2 (vel) == 0.  modify so that this is the case
				if((cmd == MidiCommand.NoteOn) && (dat2 == 0)) {
					cmd = MidiCommand.NoteOff;
				}				
				//chan = status & 0xF;
//				System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",status) + 
//						" channel : " + chan + " command : "+ cmd+" | " + str1 + " : " + dat1+" | " + str2 + " : " +dat2+ " | bytes : [ "+msgStr + " ]");
				
			} else {		//sysex or file meta event
				command = status;	
				MidiCommand cmd = MidiCommand.getVal(command);
				if(cmd == MidiCommand.FileMetaEvent) {
					procMetaEvents(msgBytes, command, stTime, msgStr);
				} else {//sysex
					System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | SysEX status : "+ String.format("0x%02X",status) + " Command : "+ cmd + " | bytes : [ "+msgStr + " ]");
				}				
			}
			
			
		}		
	}//procEvents

	//need to process bytes 2+ to find the length of the message
	private int getTrackLen(byte[] msgBytes) {		
	
		int idx = 2;
		int len =  (int)(msgBytes[idx++] & 0xFF);		
		int ch;
		//if len value is greater than 127
		if (len >= 0x80) {//if greater than half-byte-max
			len &=0x7F;
			do {
				ch = (int)(msgBytes[idx++] & 0xFF);		
				len = (len << 7) | (ch & 0x7F);
				//trklen--;
			} while (ch >= 0x80);
		}
		return len;
	}//getTrackLen
	
			
	//idx 0 will be FF, idx 1 will be type of meta event, idx 2+ will be relevant data
	public void procMetaEvents(byte[] msgBytes, int command, long stTime, String msgStr) {
		//used for string data encoded in midi msg
		int typeByte = (int)(msgBytes[1] & 0xFF);
		MidiMeta type = MidiMeta.getVal(typeByte);
		//message length is encoded in bytes 2+ - some messages can be very long, so may require more than 1 byte to encode
		int msgLength = getTrackLen(msgBytes);
		//idx 2 is message length
		//work back from msgEnd
		int msgEndIDX = msgBytes.length-1, msgSt = msgEndIDX - msgLength;
		String btsAsChar = fan.buildStrFromByteAra(msgBytes, msgSt);
		
		switch(type) {
			case SeqNumber 		:{
				//This meta-event specifies a sequence Number between 0 and 65535, used to arrange multiple 
				//tracks in a type 2 MIDI file, or to identify the sequence in which a collection of type 0 
				//or 1 MIDI files should be played. The Sequence_number meta-event should occur at Time zero, 
				//at the start of the track. 
				btsAsChar = "";
				break;} 
			case Text 			:{
				//The Text specifies the title of the track or sequence. The first Title meta-event in a 
				//type 0 MIDI file, or in the first track of a type 1 file gives the name of the work. 
				//Subsequent Title meta-events in other tracks give the names of those tracks. 
				btsAsChar = "Text : " + btsAsChar;
				break;} 
			case Copyright 		:{
				btsAsChar = "Copyright : " + btsAsChar;
				break;} 
			case TrackTitle 	:{
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
				break;} 
			case Marker 		:{
				break;} 
			case CuePoint 		:{
				break;} 
			case ChPrefix 		:{
				break;} 
			case Port 			:{
				//This meta-event specifies that subsequent events in the Track should be sent to MIDI port (bus) 
				//Number, between 0 and 255. This meta-event usually appears at the start of a track with Time zero, 
				//but may appear within a track should the need arise to change the port while the track is being played. 
				
				break;} 
			case EndTrack 		:{
				//An End_track marks the end of events for the specified Track. The Time field gives the 
				//total duration of the track, which will be identical to the Time in the last event before the End_track. 
				
				break;} 
			
			case SetTempo 		:{
				//The tempo is specified as the Number of microseconds per quarter note, between 1 and 16777215. A value of
				//corresponds to 120 bpm. To convert beats per minute to a Tempo value, divide 60,000,000 by the beats per minute. 
				int val = ((int)((msgBytes[msgEndIDX--] & 0xFF) | ((int)(msgBytes[msgEndIDX--] & 0xFF) << 8) | ((int)(msgBytes[msgEndIDX--] & 0xFF) << 16)));
				btsAsChar = "# of micros : " + val + " == " + (60000000.0f/val) + " BPM";
				break;} 
			case SMPTEOffset 	:{
				//This meta-event, which must occur with a zero Time at the start of a track, 
				//specifies the SMPTE time code at which it should start playing. The FracFrame field gives the fractional frame time (0 to 99). 
				btsAsChar = "SMPTE Offset Hr:" + (int)(msgBytes[msgEndIDX-4] & 0xFF)+"|Min:"+ (int)(msgBytes[msgEndIDX-3] & 0xFF)+"|Sec:"+ (int)(msgBytes[msgEndIDX-2] & 0xFF)+"|Fr:"+ (int)(msgBytes[msgEndIDX-1] & 0xFF)+":"+ (int)(msgBytes[msgEndIDX] & 0xFF);
				break;} 
			case TimeSig 		:{//Num, Denom, Click, NotesQ
//				The time signature, metronome click rate, and number of 32nd notes per MIDI quarter note (24 MIDI clock times) 
//				are given by the numeric arguments. Num gives the numerator of the time signature as specified on sheet music. 
//				Denom specifies the denominator as a negative power of two, for example 2 for a quarter note, 3 for an eighth note, etc. 
//				Click gives the number of MIDI clocks per metronome click, and NotesQ the number of 32nd notes in the nominal MIDI quarter 
//				note time of 24 clocks (8 for the default MIDI quarter note definition).
				int num = (int)(msgBytes[msgEndIDX-3] & 0xFF);
				nDurType denom = nDurType.getVal((int)(msgBytes[msgEndIDX-2] & 0xFF));
				int click = (int)(msgBytes[msgEndIDX-1] & 0xFF);
				int noteQ = (int)(msgBytes[msgEndIDX] & 0xFF);
				btsAsChar = "Time Sig :  " + num + " beats per measure, " + denom + " gets the beat. Click : " + click + " midi clocks per click; and " + noteQ + " 32nd notes per MIDI qtr note time (24 clocks)";
				
				break;} 
			case KeySig 		:{
//				The key signature is specified by the numeric Key value, which is 0 for the key of C, a positive value for each sharp above C, 
//				or a negative value for each flat below C, thus in the inclusive range -7 to 7. The Major/Minor field is a quoted string which 
//				will be major for a major key and minor for a minor key. 
				
				break;} 
			case SeqSpecific 	:{
				//The Sequencer_specific meta-event is used to store vendor-proprietary data in a MIDI file. 
				//The Length can be any value between 0 and 2^28 - 1, specifying the number of Data bytes (between 0 and 255) which follow. 
				//Sequencer_specific records may be very long; programs which process MIDI CSV files should be careful to protect against buffer overflows and truncation of these records. 
				break;} 
			default  :{	}						
		}				
		
		if (msgLength >= 16) {System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",command) + 
				" command : "+ MidiCommand.FileMetaEvent+" | type : " +String.format("0x%02X",typeByte) + " | type : " +type + " | msg Length : " + msgLength + " | msg as text/data : "+ btsAsChar);
		} 
		else {System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",command) + 
				" command : "+ MidiCommand.FileMetaEvent+" | type : " +String.format("0x%02X",typeByte) + " | type : " +type + " | bytes : [ "+msgStr + " ] | msg Length : " + msgLength + " | msg as text/data : "+ btsAsChar);
		}
	}//procMetaEvents
	
	public String getStringRep() {
		String res = "";
		return res;
	}
	
	
}//class trackData



//deciphered musical events (note) orderable by start time - 
//might be result of multiple midi events (note on, note off, pitch bend, etc)
class musicalEvent implements Comparable<musicalEvent>{
	//start of event - this value is the ordering value for this event
	public int stTime, duration, endTime;
	public nValType note;
	public int octave;
	
	public musicalEvent() {}

	@Override
	public int compareTo(musicalEvent othrEv) {
		if(stTime == othrEv.stTime) {return 0;}	//TODO change to 2nd tier of ordering	
		return (stTime > othrEv.stTime ? 1 : -1);
	}
	
}//class midiEvent



//class to hold a single training example data point
//this class will provide a string-representation of itself for saving feature data
class audioExampleData {
	
	public audioExampleData() {
		
	}
	
	
	
	
	
}//audioFeatureData
