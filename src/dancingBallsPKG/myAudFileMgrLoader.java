package dancingBallsPKG;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

//build audio file manager object
public class myAudFileMgrLoader implements Runnable{
	myAudioManager mgr;
	public myAudFileMgrLoader(myAudioManager _mgr) {
		mgr =_mgr;
	}
	@Override
	public void run() {
		mgr.audioFileIO = new myAudioFileManager(mgr, mgr.pa.minim,Paths.get(mgr.pa.sketchPath(),"Data"));
		mgr.setFlags(mgr.audFMgrLoadedIDX, true);
	}
	
}//myAudFileMgrLoader

class mySongLoadMapper implements Runnable{
	myAudioManager mgr;
	
	public mySongLoadMapper(myAudioManager _m){
		mgr = _m;		
	}
	@Override
	public void run() {
		mgr.pa.outStr2Scr("loadSongsAndFFT start time since start of program : "+ (mgr.pa.timeSinceStart()));
		AudioFile tmpFile;
		for(int t=0;t<mgr.songDirList.length;++t) {
			String[][] songFileNamesForType = mgr.songFilenames[t],
					songListForType = mgr.songList[t];
			String[] songBanksForType = mgr.songBanks[t];
			mySongHandler[][] tmpSongsPerType = new mySongHandler[songBanksForType.length][];
			//in case different file type gets improperly mapped - probably ignorable TODO verify
			int [][] songTypePerType = new int[songBanksForType.length][];
			for(int b=0;b<songBanksForType.length;++b) {
				mySongHandler[] tmpSongs = new mySongHandler[songFileNamesForType[b].length];
				songTypePerType[b] = new int[songFileNamesForType[b].length];
				for(int i=0;i<songFileNamesForType[b].length;++i){
					String songName =songFileNamesForType[b][i];
					if(songName.toLowerCase().contains(".mid")) {
						songTypePerType[b][i]=mgr.win.midiSong;
						tmpFile = new AudioFile(Paths.get(mgr.pa.sketchPath(),"Data",songName), songListForType[b][i], songTypePerType[b][i], null);
						tmpSongs[i]= new myMidiSongHandler(mgr.pa,mgr.pa.minim, tmpFile, mgr.songBufSize[b]);
						mgr.pa.outStr2Scr("Make Midi song "+ songName);
					} else {
						songTypePerType[b][i]=mgr.win.mp3Song;
						tmpFile = new AudioFile(Paths.get(mgr.pa.sketchPath(),"Data",songName), songListForType[b][i], songTypePerType[b][i], null);
						tmpSongs[i]= new myMP3SongHandler(mgr.pa,mgr.pa.minim, tmpFile, mgr.songBufSize[b]);						
					}
					mgr.sampleRates.put(tmpSongs[i].sampleRate, 1);}		
				tmpSongsPerType[b] = tmpSongs;
			}
			mgr.songs[t]= tmpSongsPerType;
			mgr.songTypes[t] = songTypePerType;
		}
		mgr.setFlags(mgr.audioLoadedIDX,true);
		mgr.setFlags(mgr.songHndlrLoadedIDX, true);	
		mgr.setFFTVals();
		mgr.pa.outStr2Scr("loadSongsAndFFT ended time since start of program : "+ (mgr.pa.timeSinceStart()));
	}
	
}//mySongLoadMapper

class myMidiFileProcessor implements Callable<Boolean>{
	myAudioManager mgr;
	ArrayList<AudioFile> midiFiles;
	//state of the mf analyzers : 0==not loaded, 1==loaded but not proced, 2==proced but not saved, 3==proced and saved
	public int mfaState;
	//audioCmd : what to do on call : 0==load audio, 1==process audio, 2==save audio
	private int audioCmd;
	public myMidiFileAnalyzer[] mfAnalyzerAra;
	//index in thread ara
	int idx;
	
	//# of files failed to load
	public int failedLoad;
	
	public myMidiFileProcessor(myAudioManager _mgr, ArrayList<AudioFile> _midiFiles, int _idx) {
		mgr = _mgr; idx=_idx;
		midiFiles = _midiFiles;
		mfaState = 0;
		audioCmd = 0;
		failedLoad = 0;
	}
	public int numTtlTracks;
	
	public void setToLoadAudio() {audioCmd=0;}
	public void setToProcAudio() {audioCmd=1;}
	public void setToSaveProcAudio() {audioCmd=2;}
	
	
	//either load audio, or process audio - separate 
	@Override
	public Boolean call() throws Exception {
		//build midi analyzer array and load files
		switch (audioCmd){
		case 0 :{//load
			ArrayList<myMidiFileAnalyzer> tmpMfAnalyzer = new ArrayList<myMidiFileAnalyzer>();
			for (int i=0;i<midiFiles.size();++i) {
				myMidiFileAnalyzer tmp = new myMidiFileAnalyzer(mgr, midiFiles.get(i), idx);
				boolean res = tmp.loadAudio();
				if(res) {	tmpMfAnalyzer.add(tmp);   } else {++failedLoad;}
			}	
			mfAnalyzerAra = tmpMfAnalyzer.toArray(new myMidiFileAnalyzer[0]);
			mfaState = 1;
			break;}
		case 1 :{//process
			//now analyze each file
			numTtlTracks = 0;
			for (int i=0;i<mfAnalyzerAra.length;++i) {
				mfAnalyzerAra[i].analyze();
				numTtlTracks += mfAnalyzerAra[i].numTracks;
			}
			mfaState = 2;			
			break;}
		case 2 :{//save proc'ed data
			for (int i=0;i<mfAnalyzerAra.length;++i) {
				mfAnalyzerAra[i].saveProcMidi();
			}			
			mfaState = 3;
			break;}
		}
		return true;
	}//call
	
}//myMidFileProcessor

//fire and forget midi file preprocessor
class myMidiFileProcMapper implements Runnable{
	myAudioManager mgr;	
	List<myMidiFileProcessor> callMidiProcessors;
	List<Future<Boolean>> callMidiProcFtrs;	
	
	//audioCmd : what to do on call : 0==load audio, 1==process audio, 2==save audio
	private int audioCmd;
	//state of the current midi file analysis : 0==not loaded, 1==loaded but not proced, 2==proced but not saved, 3==proced and saved
	public int curMidState;
	private String taskToDo;

	public myMidiFileProcMapper(myAudioManager _mgr) {
		mgr=_mgr;	
		callMidiProcessors = new ArrayList<myMidiFileProcessor>();
		//only run when audioFileIO is loaded - check before call
		//get ref to audio file array, to build individual threads -  audioFileIO.midiFiles has 1 arraylist of files per thread (should be 10)
 		ArrayList<AudioFile>[] midiFiles = mgr.audioFileIO.midiFiles;
 		//# of threads == midiFiles.length - build threads
 		for (int i=0;i<midiFiles.length;++i) {callMidiProcessors.add(new myMidiFileProcessor(mgr, midiFiles[i], i));}

		callMidiProcFtrs = new ArrayList<Future<Boolean>>();	
		audioCmd = 0;
		curMidState = 0;
		taskToDo = "loading all";
	}
	public boolean setToLoadAudio() {audioCmd=0; taskToDo = "loading all";return true;}
	public boolean setToProcAudio() {if(curMidState >=1) {audioCmd=1;taskToDo = "analysis of all"; return true;}return false;}
	public boolean setToSaveProcAudio() {if(curMidState >=2) {audioCmd=2; taskToDo = "saving all processed";return true;}return false;}
	
	private void loadAudio() {
 		for (int i=0;i<callMidiProcessors.size();++i) {callMidiProcessors.get(i).setToLoadAudio();}
		try {callMidiProcFtrs = mgr.pa.th_exec.invokeAll(callMidiProcessors);for(Future<Boolean> f: callMidiProcFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		
		mgr.win.clearFuncBtnSt_BuildMidiData();
	}//loadAudio
	
	private void procAudio() {
 		for (int i=0;i<callMidiProcessors.size();++i) {callMidiProcessors.get(i).setToProcAudio();}
		try {callMidiProcFtrs = mgr.pa.th_exec.invokeAll(callMidiProcessors);for(Future<Boolean> f: callMidiProcFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }				
		mgr.win.clearFuncBtnSt_ProcMidiData();
	}//procAudio
	
	private void saveAudio() {
 		for (int i=0;i<callMidiProcessors.size();++i) {callMidiProcessors.get(i).setToSaveProcAudio();}
		try {callMidiProcFtrs = mgr.pa.th_exec.invokeAll(callMidiProcessors);for(Future<Boolean> f: callMidiProcFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }				
		mgr.win.clearFuncBtnSt_SaveMidiData();
	}//saveAudio
	
	@Override
	public void run() {
		mgr.pa.outStr2Scr("Start " +taskToDo + " midi files at : "+ mgr.pa.timeSinceStart());
		switch(audioCmd) {
		case 0 :{	loadAudio();	break;}
		case 1 :{	procAudio();	break;}
		case 2 :{	saveAudio();	break;}
		}
		curMidState = audioCmd + 1;
		mgr.pa.outStr2Scr("End " +taskToDo+ " midi files at  : "+ mgr.pa.timeSinceStart());

	}//run
	
}//myMidiFileProcessor



////TODO
////setup thread structures to load audio data during program launch, to beat timeout from processing
////use these structures to load appropriate audio when UI input changes (user selects new banks)
//class myAudMgrSongLoader implements Callable<Boolean> {
//	public myAudMgrSongLoader() {
//		
//		
//	}
//	
//	//load subset of audio files in directory
//	private void loadAudio() {
//		
//	}
//	
//	@Override
//	public Boolean call() throws Exception {
//		loadAudio();
//		return true;
//	}
//	
//	
//}//myAudMgrSongLoader
//
////runnable to launch multiple threads to load audio files under specified directory
//class myAudMgrSongLoadMapper implements Runnable{
//	//runnable should call multiple callables
//	List<myAudMgrSongLoader> callSongLoaders;
//	List<Future<Boolean>> callSongLdrFtrs;	
//	myAudioManager mgr;
//	
//	public myAudMgrSongLoadMapper(myAudioManager _mgr) {
//		mgr =_mgr;
//		callSongLoaders = new ArrayList<myAudMgrSongLoader>();
//		callSongLdrFtrs = new ArrayList<Future<Boolean>>();	
//	}
//		
//	@Override
//	public void run() {
//		//TODO verify audioFileManager is loaded
//		//load song info for all files in structure
//		try {callSongLdrFtrs = mgr.pa.th_exec.invokeAll(callSongLoaders);for(Future<Boolean> f: callSongLdrFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }
//
//	}
//	
//}//myAudMgrSongLoadMapper

