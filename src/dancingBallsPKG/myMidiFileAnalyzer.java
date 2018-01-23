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
			if(status < 0xF0) {//channel event - channel mode doesn't seem to be used.
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
	
	//idx 0 will be FF, idx 1 will be type of meta event, idx 2+ will be relevant data
	public void procMetaEvents(byte[] msgBytes, int command, long stTime, String msgStr) {
		//used for string data encoded in midi msg
		String btsAsChar = fan.buildStrFromByteAra(msgBytes, 3);
		int typeByte = (int)(msgBytes[1] & 0xFF);
		MidiMeta type = MidiMeta.getVal(typeByte);					
		
		switch(type) {
			case SeqNumber 		:{
				btsAsChar = "";
				break;} 
			case Text 			:{
				break;} 
			case Copyright 		:{
				break;} 
			case TrackTitle 	:{
				break;} 
			case TrackInstName 	:{
				break;} 
			case Lyric 			:{
				break;} 
			case Marker 		:{
				break;} 
			case CuePoint 		:{
				break;} 
			case ChPrefix 		:{
				break;} 
			case Port 			:{
				break;} 
			case EndTrack 		:{
				break;} 
			case SetTempo 		:{
				//The tempo is specified as the Number of microseconds per quarter note, between 1 and 16777215. A value of
				//corresponds to 120 bpm. To convert beats per minute to a Tempo value, divide 60,000,000 by the beats per minute. 
				int val = ((int)(msgBytes[2] & 0xFF) << 16) | ((int)(msgBytes[3] & 0xFF) << 8) | (int)(msgBytes[4] & 0xFF);
				btsAsChar = "# of micros : " + val + " == " + (60000000.0f/val) + " BPM";
				break;} 
			case SMPTEOffset 	:{
				break;} 
			case TimeSig 		:{
				break;} 
			case KeySig 		:{
				break;} 
			case SeqSpecific 	:{
				break;} 
			default  :{	}						
		}				
		
		System.out.println("\tTrack "+trIDX+"| time : " +stTime + " | status : "+ String.format("0x%02X",command) + 
				" command : "+ MidiCommand.FileMetaEvent+" | type : " +String.format("0x%02X",typeByte) + " | type : " +type + " | bytes : [ "+msgStr + " ] | msg as text/data : "+ btsAsChar);
	}//procMetaEvents
	
	public String getStringRep() {
		String res = "";
		return res;
	}
	
	
}//class trackData





//class to hold a single training example data point
//this class will provide a string-representation of itself for saving feature data
class audioExampleData {
	
	public audioExampleData() {
		
	}
	
	
	
	
	
}//audioFeatureData
