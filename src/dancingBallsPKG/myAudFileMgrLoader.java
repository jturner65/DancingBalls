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

//TODO replace with multi-threaded process driven by audioFileManager object
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

class myMidFileProcessor implements Callable<Boolean>{
	myAudioManager mgr;
	ArrayList<AudioFile> midiFiles;
	
	public myMidiFileAnalyzer[] mfAnalyzer;
	//index in thread ara
	int idx;
	
	public myMidFileProcessor(myAudioManager _mgr, ArrayList<AudioFile> _midiFiles, int _idx) {
		mgr = _mgr; idx=_idx;
		midiFiles = _midiFiles;
	}
	public Long numTtlTicks;
	public int numBeats;
	@Override
	public Boolean call() throws Exception {
		//build midi analyzer array and load files
		ArrayList<myMidiFileAnalyzer> tmpMfAnalyzer = new ArrayList<myMidiFileAnalyzer>();
		for (int i=0;i<midiFiles.size();++i) {
			myMidiFileAnalyzer tmp = new myMidiFileAnalyzer(mgr, midiFiles.get(i));
			boolean res = tmp.loadAudio();
			if(res) {	tmpMfAnalyzer.add(tmp);   }
		}	
		mfAnalyzer = tmpMfAnalyzer.toArray(new myMidiFileAnalyzer[0]);
		//now analyze each file
		numTtlTicks = 0L;
		for (int i=0;i<mfAnalyzer.length;++i) {
			mfAnalyzer[i].analyze();
			numTtlTicks += mfAnalyzer[i].tickLen;
		}
		numBeats = (int) (numTtlTicks/256.0f);
		
	
		return true;
	}//call
	
	
	
	
}//myMidFileProcessor


//fire and forget midi file preprocessor
class myMidiFileProcMapper implements Runnable{
	myAudioManager mgr;	
	List<myMidFileProcessor> callMidiProcessors;
	List<Future<Boolean>> callMidiProcFtrs;		

	public myMidiFileProcMapper(myAudioManager _mgr) {
		mgr=_mgr;	
		callMidiProcessors = new ArrayList<myMidFileProcessor>();
		callMidiProcFtrs = new ArrayList<Future<Boolean>>();		
	}
	
	@Override
	public void run() {
		mgr.pa.outStr2Scr("Start analysis of midi files at : "+ mgr.pa.timeSinceStart());
		//only run when audioFileIO is loaded - check before call
 		myAudioFileManager audioFileIO = mgr.audioFileIO;
		//get ref to audio file array, to build individual threads
 		ArrayList<AudioFile>[] midiFiles = audioFileIO.midiFiles;
 		//# of threads == midiFiles.length - build threads
 		for (int i=0;i<midiFiles.length;++i) {callMidiProcessors.add(new myMidFileProcessor(mgr, midiFiles[i], i));}
		try {callMidiProcFtrs = mgr.pa.th_exec.invokeAll(callMidiProcessors);for(Future<Boolean> f: callMidiProcFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }
 		for (int i=0;i<midiFiles.length;++i) {
 			mgr.pa.outStr2Scr(""+i+" index had : " +midiFiles[i].size() + " files and loaded  : " + callMidiProcessors.get(i).mfAnalyzer.length +
 					" files, resulting in " + (midiFiles[i].size()-callMidiProcessors.get(i).mfAnalyzer.length) +
 					" failures | # beats : " + callMidiProcessors.get(i).numBeats); 			
 		}
		
		mgr.clearPreProcMidi();
		//need to set flag so that this isn't done repeatedly
		mgr.pa.outStr2Scr("End analysis of midi files at : "+ mgr.pa.timeSinceStart());
	}	
	
}//myMidiFileProcessor



//TODO
//setup thread structures to load audio data during program launch, to beat timeout from processing
//use these structures to load appropriate audio when UI input changes (user selects new banks)
class myAudMgrSongLoader implements Callable<Boolean> {
	public myAudMgrSongLoader() {
		
		
	}
	
	//load subset of audio files in directory
	private void loadAudio() {
		
	}
	
	@Override
	public Boolean call() throws Exception {
		loadAudio();
		return true;
	}
	
	
}//myAudMgrSongLoader

//runnable to launch multiple threads to load audio files under specified directory
class myAudMgrSongLoadMapper implements Runnable{
	//runnable should call multiple callables
	List<myAudMgrSongLoader> callSongLoaders;
	List<Future<Boolean>> callSongLdrFtrs;	
	myAudioManager mgr;
	public myAudMgrSongLoadMapper(myAudioManager _mgr) {
		mgr =_mgr;
		callSongLoaders = new ArrayList<myAudMgrSongLoader>();
		callSongLdrFtrs = new ArrayList<Future<Boolean>>();	
	}
		
	@Override
	public void run() {
		//TODO verify audioFileManager is loaded
		//load song info for all files in structure
		try {callSongLdrFtrs = mgr.pa.th_exec.invokeAll(callSongLoaders);for(Future<Boolean> f: callSongLdrFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }

	}
	
}//myAudMgrSongLoadMapper

