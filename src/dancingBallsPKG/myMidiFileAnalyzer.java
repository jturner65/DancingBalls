package dancingBallsPKG;

import java.io.*;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.sound.midi.*;

//a class that provides functionality to load a midi file and save analysis data
public class myMidiFileAnalyzer {
    //ref to info about file from audioFileManager
    public AudioFile fileListing;
    public Sequence sequence;
    public MidiFileFormat format;
    public String composer;
    public int thdIDX;
    
    public boolean loadedSeq;
    private ConcurrentSkipListMap<Long,Float> noteHistogram;
    public int numTracks;
    public long tickLen;
    
    
    private myMidiSongData midiSong;
    
    public int midiFileType; //either 0, 1, or 2
    //pass audio manager, file listing for midi song, and thread idx (if used, otherwise use 0) and this will build a midi song encoding of the midi data
    public myMidiFileAnalyzer(AudioFile _fileListing, int _thIdx) {
        //mgr = _mgr;
        thdIDX = _thIdx;
        fileListing = _fileListing;
        composer = (fileListing.composerDir == null ? "Unknown Composer" : fileListing.composerDir.dispName);
        //System.out.println("composer : " + composer);
        loadedSeq = false;
    }

    public boolean loadAudio() {
        try{            //load sequence
            File f = fileListing.filePath.toFile();
            if(f==null) {
                System.out.println("Null File obj : file not found : " + fileListing.filePath);
                return false;
            }
            sequence = MidiSystem.getSequence(f);
            format = MidiSystem.getMidiFileFormat(f);
            loadedSeq = true;
            midiSong =  new myMidiSongData(this,format, sequence);                
        }
        catch( InvalidMidiDataException ex ){
            System.out.println("This file is not a valid midi file : "+ fileListing.filePath+": format : " + format + "\t | msg : "+ex.getMessage() );
            sequence = null;
            format  = null;
            loadedSeq = false;
            midiSong = null;                
            return false;}
        catch( IOException ex ) { 
            System.out.println("Had a problem accessing this midi file : "+ fileListing.filePath+":\t"+ex.getMessage() );
            sequence = null;
            format  = null;
            loadedSeq = false;
            midiSong = null;                
            return false;}
        return true;
    }//loadAudio
    
    //have everything in ticks - maybe should use location in ticks to define training examples    
    public void analyze() {
        if(!loadedSeq) {return;}
        //midiSong =  new myMidiSongData(this,format, sequence);    
        midiSong.procMidiSongData();
        noteHistogram = midiSong.getNoteHistogram();
        tickLen = midiSong.tickLen;
        numTracks = midiSong.numTracks;
        
    }//analyze    
    
    //return a snapshot of winWidth values of song note levels starting at stTime, avged over bins of binSize
    public float[] getRelSongLevelsWin(long stTime, int winWidth, int binSize) {
        float[] songLvls = new float[winWidth];
        long endTime = stTime + (winWidth * binSize);
        int destIdx = 0;
        if(binSize == 1) {
            for (long i=stTime; i<endTime; ++i) {
                Float lvl = noteHistogram.get(i);
                if(null==lvl) {++destIdx;continue;}
                songLvls[destIdx++] = lvl;
            }                    
        } else {
            long i= stTime, winEnd;
            //avg lvls over bins - might not have winWidth bins, remaining ones are 0
            while(i<endTime) {
                if (i + binSize >= endTime) {         winEnd = endTime;    } 
                else {                                winEnd = i+binSize;    }
                float lvl = 0.0f, count = 0.0f;
                //average over individual bin windows
                for(long j=i;j<winEnd;++j) {
                    Float lvltmp = noteHistogram.get(j);
                    if(null==lvltmp) {continue;}
                    lvl+= lvltmp;
                    ++count;
                }
                songLvls[destIdx++] = count==0? 0 : lvl/count;
                i=winEnd;
            }        
        }        
        return songLvls;
    }//getRelSongLevelsWin
    

    
    //write the results of the audio processing
    public void saveProcMidi() {
        if(!loadedSeq) {return;}
        if(!midiSong.procDone) {System.out.println("Midi Song processing not complete : " + midiSong.title + " not processed so cannot save;"); return;    }
        midiSong.saveData();    
        
        //testing - this also alleviates memory issues
        midiSong = null;
    }//saveProcMidi    
    
}//myMidiFileAnalyzer



