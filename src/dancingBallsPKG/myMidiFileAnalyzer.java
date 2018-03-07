package dancingBallsPKG;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.sound.midi.*;

//a class that provides functionality to load a midi file and save analysis data
public class myMidiFileAnalyzer {
	myAudioManager mgr;
	//ref to info about file from audioFileManager
	public AudioFile fileListing;
	public Sequence sequence;
	public MidiFileFormat format;
	public int byteLength, type, numTracks;
	public Long tickLen;
	public String composer;
	public int thdIDX;
	
	public boolean loadedSeq;
	
	public myMidiSongData midiSong;
	
	public int midiFileType; //either 0, 1, or 2
		
	public myMidiFileAnalyzer(myAudioManager _mgr,AudioFile _fileListing, int _thIdx) {
		mgr = _mgr;thdIDX = _thIdx;
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
	
	//have everything in ticks - maybe should use location in ticks to define training examples	
	public void analyze() {
		if(!loadedSeq) {return;}
		midiSong =  new myMidiSongData(this,format, sequence);		
	}//analyze	
	
	//write the results of the audio processing
	public void saveProcMidi() {
		if(!loadedSeq) {return;}
		if(!midiSong.procDone) {mgr.pa.outStr2Scr("Midi Song processing not complete : " + midiSong.title + " not processed so cannot save;"); return;	}
		midiSong.saveData();		
	}//saveProcMidi	
	
}//myMidiFileAnalyzer



