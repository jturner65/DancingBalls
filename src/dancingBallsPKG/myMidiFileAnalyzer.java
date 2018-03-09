package dancingBallsPKG;

import java.io.*;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.sound.midi.*;

//a class that provides functionality to load a midi file and save analysis data
public class myMidiFileAnalyzer {
	//private myAudioManager mgr;
	//ref to info about file from audioFileManager
	public AudioFile fileListing;
	public Sequence sequence;
	public MidiFileFormat format;
	public String composer;
	public int thdIDX;
	
	public boolean loadedSeq;
	
	public myMidiSongData midiSong;
	
	public int midiFileType; //either 0, 1, or 2
	//pass audio manager, file listing for midi song, and thread idx (if used, otherwise use 0) and this will build a midi song encoding of the midi data
	public myMidiFileAnalyzer(AudioFile _fileListing, int _thIdx) {
		//mgr = _mgr;
		thdIDX = _thIdx;
		fileListing = _fileListing;
		composer = (fileListing.composerDir == null ? "Unknown Composer" : fileListing.composerDir.dispName);
		loadedSeq = false;
	}

	public boolean loadAudio() {
		try{		    //load sequence
			File f = fileListing.filePath.toFile();
		    if(f==null) {
		    	System.out.println("Null File obj : file not found : " + fileListing.filePath);
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
	
	
	//returns a map  of relative volume for every tick in this song - 0->lvlMult
	public float[] getRelSongLevels(float lvlMult){
		float[] songLvls;
		Integer[] intSongLvls = midiSong.noteHistogram.values().toArray(new Integer[0]);
		int minLvl = 100000000, maxLvl = -1;
		for(int i=0;i<intSongLvls.length;++i) {
			if(intSongLvls[i]>maxLvl) {maxLvl = intSongLvls[i];} else if(intSongLvls[i]<minLvl) {minLvl = intSongLvls[i];}
		}
		songLvls = new float[intSongLvls.length];
		for(int i=0;i<intSongLvls.length;++i) {	songLvls[i]=lvlMult*(intSongLvls[i]-minLvl)/(1.0f*maxLvl);}
		return songLvls;
	}//getRelSongLevels
	
	//write the results of the audio processing
	public void saveProcMidi() {
		if(!loadedSeq) {return;}
		if(!midiSong.procDone) {System.out.println("Midi Song processing not complete : " + midiSong.title + " not processed so cannot save;"); return;	}
		midiSong.saveData();		
	}//saveProcMidi	
	
}//myMidiFileAnalyzer



