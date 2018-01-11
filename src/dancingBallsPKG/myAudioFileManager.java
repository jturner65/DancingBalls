package dancingBallsPKG;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

import ddf.minim.Minim;

//classes to manage all audio file IO - loading files, maintaining directory structure information, etc.
//the purpose of these classes is to speed up IO, minimize loaded auido footprint, and facilitate accessing songs on disk

/**
 * functionality class to manage the audio file IO
 * @author john
 */
public class myAudioFileManager {
	public DancingBalls pa;
	//owning audio manager
	public myAudioManager mgr;
	//owning window 
	public DancingBallWin win;
	//reference to minim functionality
	public Minim minim;	
	//root directory containing all audio files
	private AudioDir root;	

	public myAudioFileManager(DancingBalls _pa, DancingBallWin _win, myAudioManager _mgr, Minim _minim, Path _rootDirPath) {
		pa=_pa;win=_win;mgr=_mgr;minim=_minim;
		root = new AudioDir(_rootDirPath,_rootDirPath.toString(), null);			
		//pa.outStr2Scr("Total # of files : " + AudioDir.numFiles);
	}//myAudioFileManager
	
	//root holds either midi or mp3 dirs, directories under these are banks, files under each bank directory, and their subdirectories, are songs
	//
	//get # of types listed in data == # of subdirectories
	public int getNumSongTypes() {	return root.getNumSubDirs();}

	//given type of song (subdir under data root) how many banks are available NOTE : not subdir idx, but song type
	public int getNumSongBanks(int type) {
		AudioDir subdir = getTypeSubdir(type);	
		if(subdir == null) {return 0;}
		return subdir.getNumSubDirs();//subdirs of directories under root are banks		
	}//
	
	public AudioDir getTypeSubdir(int type) {
		//first get subdir matching type
		String chkStr;
		if(type==win.midiSong) {		chkStr="midi";} 
		else if(type==win.mp3Song) {	chkStr="mp3";} 
		else {//unknown type
			return null;
		}
		return root.getSubDirByName(chkStr);	
	}
	
	//return specific type directory ref
	public AudioDir getTypeSubdirByIDX(int idx) {return root.getSubDirByIDX(idx);}
	//return list of type subdirectories (ara holding "midi" and "mp3" currently")
	public String[] getTypeSubdirNames() {return root.getSubDirNames();	}

	//return list of a specific type's banks
	public String[] getBanksInType(int typeIDX) {
		AudioDir typeSubDir = root.getSubDirByIDX(typeIDX);	
		if(typeSubDir == null) {return new String[0];}
		return typeSubDir.getSubDirNames();		
	}//getBanksInType
	
	//given a particular bank disp name, get # of songs in this bank (including all sub-banks)
	//pass idx of type directory, idx of bank
	public int getNumSongsInBank(int typeIDX, int bankIDX) {
		AudioDir typeSubDir = root.getSubDirByIDX(typeIDX);	
		if(typeSubDir == null) {return 0;}
		AudioDir bankSubDir = typeSubDir.getSubDirByIDX(bankIDX);	
		if(bankSubDir == null) {return 0;}
		return bankSubDir.getNumAudioFiles();		
	}//getNumSongsInBank
	
	//return list of display names of songs in bank
	//pass idx of type directory, idx of bank
	public String[] getSongsInBank(int typeIDX, int bankIDX) {
		AudioDir typeSubDir = root.getSubDirByIDX(typeIDX);	
		if(typeSubDir == null) {return new String[0];}
		AudioDir bankSubDir = typeSubDir.getSubDirByIDX(bankIDX);	
		if(bankSubDir == null) {return new String[0];}
		return bankSubDir.getAudioFileNames();
	}//getSongsInBank
	

}//class myAudioFileManager 

//maintain info about a single audio file on disk and its status as loaded or not - only file/io related info
class AudioFile{
	//fully qualified file name (to navigate to this object in OS) and (shortened) display name
	protected Path filePath;
	//shortened display name
	public String dispName, chkName;
	//type is 0 if mp3/wav, 1 if midi, types < 0 are unhandled
	//-1 is directory, -2 is unhandled file type (not a song file or a midi file)
	public int type;
	//whether or not this file is loaded (with a song handler built)
	protected boolean isLoaded;
	//the song hanlder handling this file, if loaded, null otherwise
	protected mySongHandler hndlr;
	//containing directory - if null then this is root directory (must be type -1)
	public AudioDir parentDir;
	//depth in hierarchy - 0 == is root, 1 == in root dir, etc
	public int lvl=0;
	
	public AudioFile( Path _fPath,String _dname, int _type, AudioDir _parentDir) {
		filePath=_fPath;dispName=_dname; chkName=dispName.toLowerCase();type=_type;parentDir=_parentDir;
		if(parentDir != null) {lvl = parentDir.lvl+1;}
		isLoaded=false;
		hndlr = null;
	}
	//check if loaded
	public boolean isLoaded() {return isLoaded;}	
	//call to unload this song if loaded
	public void unload() {	isLoaded=false;	hndlr = null;}
	//call to load song into memory - used to easily maintain record of songs currently loaded into song handlers
	public String getFileNameForLoad(mySongHandler _hndlr){
		if(type < 0) {return null;}//don't load directories or unhandled files
		isLoaded = true;
		hndlr=_hndlr;		
		return filePath.toString();
	}// getFileNameForLoad()
	
}//class AudioFile

//class to maintain info about a directory holding audio data on disk, including references to sub dirs and contained files
class AudioDir extends AudioFile{
	//subdirectories under this directory, keyed by display name
	//private ArrayList<AudioDir> subDirs;
	private ConcurrentSkipListMap<String, AudioDir> subDirs;
	//files explicitly held in this directory, keyed by display name
	//private ArrayList<AudioFile> audioFiles;
	private ConcurrentSkipListMap<String, AudioFile> audioFiles;
	//array of display names of subdir names and audio file names - to facilitate consistent access by idx
	private String[] subDirsNames, audioFilesNames;	
	
	//# of audio files in entire structure
	public static int numFiles = 0;
	
	public AudioDir(Path _fPath,String _dname, AudioDir _parent) {
		super(_fPath,_dname,-1,_parent);
		loadDirStruct();
	}
	
	//load directory structure, making new AudioFiles and AudioDirs for elements in structure, 
	//and recursing through them all to have a hierarchical image of data directory structure
	public void loadDirStruct() {
		//verify fileName is directory
		String dirName = filePath.toString();
		File folder = new File(dirName);
		subDirs = new ConcurrentSkipListMap<String, AudioDir>();
		audioFiles = new ConcurrentSkipListMap<String, AudioFile>();
		if(!folder.isDirectory()) {
			System.out.println("****************** !!!! file name : " + dirName + " is not a directory");
			return;
		}
		File[] listOfFiles = folder.listFiles();
		for (int i = 0; i < listOfFiles.length; i++) {
			Path fPath = Paths.get(dirName, listOfFiles[i].getName());
			String tmpDispName = listOfFiles[i].getName();
			if (listOfFiles[i].isFile()) {
				String[] nameParts = tmpDispName.split("[-.]+");//("\\.");
				String ext = nameParts[nameParts.length-1].toLowerCase();				//extension is always last component of file name
				int type = 0;															//default file time : assumes some kind of song
				if(ext.contains("mp3")) {			type=0; } 							//handled song files - broken out to add more options easily
				else if(ext.contains("wav")) {		type=0; } 
				else if(ext.contains("mid")) {		type=1;	} 							//midi file	
				else if(ext.contains("zip")){		type=-3;	} 						//zip
				else {								type=-2;}							//not handled song file or midi file
				
				if(type == -3) {
					System.out.println("Unhandled : parent directory " + this.filePath.toString() + " contains file name : " + tmpDispName + " | ext : " + ext + " type : " + type);
				}
				if(type>=0) {//<0 means not a handled audio file type
					++numFiles;
					String newDispName = nameParts[0];
					for(int j=1;j<nameParts.length-1;++j) {newDispName = newDispName + "."+nameParts[j];}//reconstruct names that had periods in them
					AudioFile tmp = new AudioFile(fPath, newDispName, type,this);
					//System.out.println("new disp name : " + tmp.dispName);
					audioFiles.put(tmp.dispName,tmp);
				}
		    } else if (listOfFiles[i].isDirectory()) {									//directory
				//System.out.println("------->Directory name : " + dispName );
		    	AudioDir tmp = new AudioDir(fPath, tmpDispName, this);
				//System.out.println("------->End files under Directory name : " + dispName + " | directory has : " + tmp.getNumAudioFiles() + " audio files and " + tmp.getNumSubDirs() + " subdirs\n" );
		    	subDirs.put(tmp.dispName,tmp);
			} else {System.out.println("Under Dir " + dirName + " found neither File nor Directory : " + listOfFiles[i].getName());}//neither file nor directory, probably not possible
		}//get OS list of all elements under fileName directory
		//set up arrays of keyset names
		subDirsNames= subDirs.keySet().toArray(new String[0]);
		audioFilesNames = audioFiles.keySet().toArray(new String[0]);
	}//loadDirStruct()
	
	//UI-consumed lists
	//return list of sub directory names in this sub directory
	public String[] getSubDirNames() { return subDirsNames;}	
	public int getNumSubDirs() {	return subDirs.size();}
	public ConcurrentSkipListMap<String, AudioDir> getSubDirs(){return subDirs;}
	public AudioDir getSubDirByName(String nameStr) {		return subDirs.get(nameStr);	}
	//get subdir by index in ara
	public AudioDir getSubDirByIDX(int idx) {return subDirs.get(subDirsNames[idx]);}
	
	//audio files
	//return list of all audiofile names in this sub directory
	public String[] getAudioFileNames() {return audioFilesNames;}
	public int getNumAudioFiles() { return audioFiles.size();}
	public ConcurrentSkipListMap<String, AudioFile> getAudioFiles(){return audioFiles;}
	public AudioFile getAudioFileByName(String nameStr) {		return audioFiles.get(nameStr);	}
	//return specific audio file under this sub directory given specific idx of name
	public AudioFile getAudioFileByIDX(int idx) { return audioFiles.get(audioFilesNames[idx]);}
	
	
}//class AudioDir