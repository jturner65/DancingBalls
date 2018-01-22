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
		System.out.println("Composer : " +composer + "\tSong Name : " + fileListing.dispName +"\tFormat info : byte len : " + byteLength+" | midi type : " + type + " | # of tracks  : "+ numTracks);
		//read midi message data in for each track
		
		//may be short message (all but sys-ex and meta data like key)
		// ShortMessage: http://docs.oracle.com/javase/8/docs/api/javax/sound/midi/ShortMessage.html
		// what Data1 and Data2 will be, refer to the midi spec: http://www.midi.org/techspecs/midimessages.php

		for(int i=0;i<tracks.length;++i) {
			trackData tmpTrk = new trackData(this, tracks[i], i);
			
		}
		
		
		
	
		
	}
	
}//myMidiFileAnalyzer

//class to hold all data for a single track from a midi file - format this data to save as features
class trackData{
	int numEvents;
	Track trk;
	ArrayList<MidiMessage>msgs;
	myMidiFileAnalyzer fan;
	
	public trackData(myMidiFileAnalyzer _fan, Track _trk, int i) {
		fan=_fan;
		trk = _trk;
		numEvents = trk.size();
		msgs = new ArrayList<MidiMessage>();
		for(int e=0;e<numEvents;++e) {
			MidiMessage ev = trk.get(e).getMessage();
			msgs.add(ev);
			if(i==0) {
				int status = ev.getStatus();
				byte[] msgBytes = ev.getMessage();//(int)(byte & 0xFF)
				String msgStr = ""+(int)(msgBytes[0]& 0xFF);
				for(int b=1;b<msgBytes.length;++b) {
					msgStr +=","+(int)(msgBytes[b]& 0xFF);
				}
				//if(status != 255) {
					//System.out.println("\tTrack "+i+" event idx "+ e+" : status byte "+ status+" | bytes : [ "+msgStr + " ]");
				//}
			}
		}
	}
}//class trackData

//class to hold a single training example data point
//this class will provide a string-representation of itself for saving feature data
class audioExampleData {
	
	public audioExampleData() {
		
	}
	
	
	
	
	
}//audioFeatureData
