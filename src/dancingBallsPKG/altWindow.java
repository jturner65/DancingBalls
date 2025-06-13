package dancingBallsPKG;

import java.util.ArrayList;
import java.util.List;

public class altWindow extends Base_DispWindow {

	//piano visualization object
	public myPianoObj dispPiano;	

	/////////////
	// ui objects 
	////////////
	//sim timestep from ui
	public float deltaT = .01f;
	//list value to use - save idx
	public int songType = 0;
	//idxs - need one per object
	public final static int
		gIDX_TimeStep 		= 0,
		gIDX_SongTypes		= 1;
	//initial values - need one per object
	public float[] uiVals = new float[]{
			deltaT,											//	timestep
			songType
	};			//values of 8 ui-controlled quantities
	public final int numGUIObjs = uiVals.length;	
	public String[] songTypeList = new String[]{"Classical","Dance","Jazz"};
	/////////
	//ui button names -empty will do nothing, otherwise add custom labels for debug and custom functionality names
	public String[] menuDbgBtnNames = new String[]{};
	public String[] menuFuncBtnNames = new String[]{};

	//////////////
	// local/ui interactable boolean buttons
	//////////////
	//private child-class flags - window specific
	//for every class-wide boolean make an index, and increment numPrivFlags.
	//use getPrivFlags(idx) and setPrivFlags(idx,val) to consume
	//put idx-specific code in case statement in setPrivFlags
	public static final int 
			debugAnimIDX 		= 0,					//debug
			showPianoKbd 		= 1;					//show piano
	public static final int numPrivFlags = 2;

	public altWindow(DancingBalls _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed,
			String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
		float stY = rectDim[1]+rectDim[3]-4*yOff,stYFlags = stY + 2*yOff;
		trajFillClrCnst = DancingBalls.gui_DarkCyan;		//override this in the ctor of the instancing window class
		trajStrkClrCnst = DancingBalls.gui_Cyan;
		super.initThisWin(_canDrawTraj, true, false);
	}
	
	@Override
	protected void initMe() {
		//piano to display on size of window
		dispPiano = new myPianoObj(fillClr, rectDim);		//start with 52 white keys (full keyboard)
		//called once
		initPrivFlags(numPrivFlags);
		//this window is runnable
		setFlags(isRunnable, true);
		//this window uses a customizable camera
		setFlags(useCustCam, true);
		//initial local flags
		setPrivFlags(showPianoKbd,true);
		//set offset to use for custom menu objects
		custMenuOffset = uiClkCoords[3];	
		
		//put other initialization stuff here
		
	}//
	//initialize all UI buttons here
	@Override
	public void initAllUIButtons() {
		//give true labels, false labels and specify the indexes of the booleans that should be tied to UI buttons
		truePrivFlagNames = new String[]{			//needs to be in order of privModFlgIdxs
				"Debugging","Hide Piano"
		};
		falsePrivFlagNames = new String[]{			//needs to be in order of flags
				"Enable Debug","Show Piano"
		};
		privModFlgIdxs = new int[]{					//idxs of buttons that are able to be interacted with
				debugAnimIDX,showPianoKbd
		};
		numClickBools = privModFlgIdxs.length;	
		initPrivBtnRects(0,numClickBools);
	}
	
	//add reference here to all button IDX's 
	@Override
	public void setPrivFlags(int idx, boolean val) {
		boolean curVal = getPrivFlags(idx);
		if(val == curVal) {return;}
		int flIDX = idx/32, mask = 1<<(idx%32);
		privFlags[flIDX] = (val ?  privFlags[flIDX] | mask : privFlags[flIDX] & ~mask);
		switch(idx){
			case debugAnimIDX 			: {
				break;}
			case showPianoKbd			: {
				break;}
		}
	}
	
	//initialize structure to hold modifiable menu regions
	@Override
	protected void setupGUIObjsAras(){	
		//pa.outStr2Scr("setupGUIObjsAras start");
		guiMinMaxModVals = new double [][]{
			{0,1.0f,.001f},						//timestep           		gIDX_TimeStep 	
			{0,songTypeList.length-1 ,1}
		};		//min max modify values for each modifiable UI comp	

		guiStVals = new double[]{
			uiVals[gIDX_TimeStep],
			uiVals[gIDX_SongTypes]
		};								//starting value
		
		guiObjNames = new String[]{
				"Time Step",
				"Song Type"
		};								//name/label of component	
		
		//idx 0 is treat as int, idx 1 is obj has list vals, idx 2 is object gets sent to windows
		guiBoolVals = new boolean [][]{
			{false, false, true},	//timestep           		gIDX_TimeStep 
			{true, true, true}
		};						//per-object  list of boolean flags
		
		//since horizontal row of UI comps, uiClkCoords[2] will be set in buildGUIObjs		
		guiObjs_Numeric = new myGUIObj[numGUIObjs];			//list of modifiable gui objects
		if(numGUIObjs > 0){
			buildGUIObjs(guiObjNames,guiStVals,guiMinMaxModVals,guiBoolVals,new double[]{xOff,yOff});			//builds a horizontal list of UI comps
		}
//		setupGUI_XtraObjs();
	}//setupGUIObjsAras
	
	//all ui objects should have an entry here to show how they should interact
	@Override
	protected void setUIWinVals(int UIidx) {
		float val = (float)guiObjs_Numeric[UIidx].getVal();
		float oldVal = uiVals[UIidx];
		//int ival = (int)val;
		if(val != uiVals[UIidx]){//if value has changed...
			uiVals[UIidx] = val;
			switch(UIidx){		
			case gIDX_TimeStep 			:{
				if(val != deltaT){deltaT = val;}
				break;}
			case gIDX_SongTypes 		:{
				songType = (int)val;
				break;}
			}
		}//if val is different
	}//setUIWinVals
	
	//handle list ui components - return display value for list-based UI object
	@Override
	protected String getUIListValStr(int UIidx, int validx) {
		switch(UIidx){
		case gIDX_SongTypes :{ return songTypeList[validx %songTypeList.length];}
		default : {break;}
	}
	return "";	}

	

	
	@Override
	protected void drawMe(float animTimeMod) {
		//all drawing stuff goes here
		

	}
	
	//draw stuff to put on screen as 2d text/images
	@Override
	protected void drawOnScreenStuffPriv(float modAmtMillis) {
		//draw all 2d screen data here, super-imposed over background
		if (getPrivFlags(showPianoKbd)){
			dispPiano.drawMe(pa, true);//change to local boolean flag if want to control whether notes are shown
		}
	}

	@Override
	//put information in right-side hideable window for this window
	protected void drawRightSideInfoBar(float modAmtMillis) {		
		
	}
	
	@Override
	public void drawCustMenuObjs() {
		pa.pushMatrix();				pa.pushStyle();		
		//all sub menu drawing within push mat call
		pa.translate(5,custMenuOffset+yOff);
		//draw any custom menu stuff here
		
		
		pa.popStyle();					pa.popMatrix();		
	}
	
	//any simulation stuff - executes before draw on every draw cycle
	@Override
	//modAmtMillis is time passed per frame in milliseconds - returns if sim is done
	protected boolean simMe(float modAmtSec) {
		
		return true;
	}
	
	@Override
	protected void closeMe() {
		//things to do when swapping this window out for another window - release objects that take up a lot of memory, for example.
	}
	
	
	@Override
	protected void showMe() {
		//things to do when swapping into this window - reinstance released objects, for example.
		pa.setMenuDbgBtnNames(menuDbgBtnNames);
		pa.setMenuFuncBtnNames(menuFuncBtnNames);		
		
	}
	
	@Override
	protected void stopMe() {}


	//custom functions launched by UI input
	//if launching threads for custom functions, need to remove clearFuncBtnState call in function below and call clearFuncBtnState when thread ends
	private void custFunc0(){	
		//custom function code here
		clearFuncBtnState(0,false);
	}		
	private void custFunc1(){	
		//custom function code here
		clearFuncBtnState(1,false);
	}		
	private void custFunc2(){	
		//custom function code here
		clearFuncBtnState(2,false);
	}			
	private void custFunc3(){	
		//custom function code here
		clearFuncBtnState(3,false);
	}			
	private void custFunc4(){	
		//custom function code here
		clearFuncBtnState(4,false);
	}		
	@Override
	public void clickFunction(int btnNum) {
		pa.outStr2Scr("click cust function in "+name+" : btn : " + btnNum);
		switch(btnNum){
			case 0 : {	custFunc0();	break;}
			case 1 : {	custFunc1();	break;}
			case 2 : {	custFunc2();	break;}
			case 3 : {	custFunc3();	break;}
			case 4 : {	custFunc4();	break;}
			default : {break;}
		}	
	}		//only for display windows
	
	//debug functions
	//if launching threads for debugging, need to remove clearDBGState call in function below and call clearDBGState when thread ends
	private void dbgFunc0(){	
		//dbg code here
		clearDBGBtnState(0,false);
	}	
	private void dbgFunc1(){		
		//dbg code here
		clearDBGBtnState(1,false);
	}	
	private void dbgFunc2(){		
		//dbg code here
		clearDBGBtnState(2,false);
	}	
	private void dbgFunc3(){		
		//dbg code here
		clearDBGBtnState(3,false);
	}	

	@Override
	public void clickDebug(int btnNum){
		pa.outStr2Scr("click debug in "+name+" : btn : " + btnNum);
		switch(btnNum){
			case 0 : {	dbgFunc0();	break;}
			case 1 : {	dbgFunc1();	break;}
			case 2 : {	dbgFunc2();	break;}
			case 3 : {	dbgFunc3();	break;}
			default : {break;}
		}		
	}//clickDebug

	@Override
	protected void setCameraIndiv(float[] camVals) {
		pa.camera(camVals[0],camVals[1],camVals[2],camVals[3],camVals[4],camVals[5],camVals[6],camVals[7],camVals[8]);      
		// puts origin of all drawn objects at screen center and moves forward/away by dz
		pa.translate(camVals[0],camVals[1],(float)dz); 
	    pa.setCamOrient();	
	}
	
	@Override
	public void hndlFileLoadIndiv(String[] vals, int[] stIdx) {}
	@Override
	public List<String> hndlFileSaveIndiv() {List<String> res = new ArrayList<String>();return res;}
	@Override
	protected void processTrajIndiv(myDrawnSmplTraj drawnNoteTraj){	}
	@Override
	protected boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld){return false;}
	//alt key pressed handles trajectory
	//cntl key pressed handles unfocus of spherey
	@Override
	protected boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn) {	boolean res = checkUIButtons(mouseX, mouseY);	return res;}//hndlMouseClickIndiv
	@Override
	protected boolean hndlMouseDragIndiv(int mouseX, int mouseY, int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld, int mseBtn) {boolean res = false;return res;}	
	@Override
	protected myPoint getMsePtAs3DPt(int mouseX, int mouseY){return pa.P(mouseX,mouseY,0);}
	@Override
	protected void snapMouseLocs(int oldMouseX, int oldMouseY, int[] newMouseLoc) {}	
	@Override
	protected void hndlMouseRelIndiv() {}
	@Override
	protected void endShiftKey_Indiv() {}
	@Override
	protected void endAltKey_Indiv() {}
	@Override
	protected void endCntlKey_Indiv() {}
	@Override
	protected void addSScrToWinIndiv(int newWinKey){}
	@Override
	protected void addTrajToScrIndiv(int subScrKey, String newTrajKey){}
	@Override
	protected void delSScrToWinIndiv(int idx) {}	
	@Override
	protected void delTrajToScrIndiv(int subScrKey, String newTrajKey) {}
	@Override
	protected void resizeMe(float scale) {		dispPiano.updateGridXandY( rectDim);		}

	@Override
	protected void initDrwnTrajIndiv() {}


}
