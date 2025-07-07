package dancingBallsPKG;

import java.util.*;

import processing.core.*;

/**
 * abstract class to hold base code for a menu/display window (2D for gui, etc), 
 * to handle displaying and controlling the window, and calling the implementing 
 * class for the specifics
 * @author john
 *
 */
public abstract class Base_DispWindow {
    public DancingBalls pa;
    public static int winCnt = 0;
    public int ID;    
    public String name, winText;        
    public int[] fillClr, strkClr, rtSideUIFillClr, rtSideUIStrkClr;
    public int trajFillClrCnst, trajStrkClrCnst;
    public float[] rectDim, closeBox, rectDimClosed, mseClickCrnr;    

    public static final float xOff = DancingBalls.getTextSize();
    public static final float yOff = DancingBalls.getTextHeightOffset();
    public static final float btnLblYOff = 2 * yOff;
    public static final float rowStYOff = yOff*.15f;
    public static final int topOffY = 40;            //offset values to render boolean menu on side of screen - offset at top before drawing
    public static final float clkBxDim = DancingBalls.getClkBoxDim();
    
    public int pFlagIdx;                    //the flags idx in the PApplet that controls this window - use -1 for none    
    private int[] dispFlags;    
    public static final int 
                showIDX             = 0,            //whether or not to show this window
                is3DWin             = 1,
                canChgView            = 2,            //view can change
                isRunnable             = 3,            //runs a simulation
                closeable             = 4,            //window is able to be closed
                hasScrollBars         = 5,            //this window has scroll bars (both vert and horizontal)
                canDrawTraj         = 6,            //whether or not this window will accept a drawn trajectory
                drawingTraj         = 7,            //whether a trajectory is being drawn in this window - all windows handle trajectory input, has different functions in each window
                editingTraj         = 8,            //whether a trajectory is being edited in this window
                showTrajEditCrc     = 9,            //set this when some editing mechanism has taken place - draw a circle of appropriate diameter at mouse and shrink it quickly, to act as visual cue
                smoothTraj             = 10,            //trajectory has been clicked nearby, time to smooth
                trajDecays             = 11,            //drawn trajectories eventually/immediately disappear
                trajPointsAreFlat     = 12,            //trajectory drawn points are flat (for pick, to prevent weird casting collisions                
                procMouseMove         = 13,
                mouseSnapMove        = 14,            //mouse locations for this window are discrete multiples - if so implement inherited function to calculate mouse snap location
                uiObjMod            = 15,            //a ui object in this window has been modified
                useRndBtnClrs        = 16,    
                useCustCam            = 17,            //whether or not to use a custom camera for this window
                drawMseEdge            = 18,            //whether or not to draw the mouse location/edge from eye/projection onto box
                drawRightSideMenu    = 19,            //whether this window has a right-side info menu overlay
                showRightSideMenu    = 20,            //whether this window is currently showing right side info menu, or if it is minimized
                clearPrivBtns        = 21;            //momentary priv buttons have been set, need to be cleared next frame
                
    public static final int numDispFlags = 22;
    
    //private window-specific flags and UI components (buttons)
    public int[] privFlags;
    public String[] truePrivFlagNames; //needs to be in order of flags    
    public String[] falsePrivFlagNames;//needs to be in order of flags
    
        //for boolean buttons based on child-class window specific values
    public int[][] privFlagColors;
    public int[] privModFlgIdxs;                                        //only modifiable idx's will be shown as buttons - this needs to be in order of flag names
    public float[][] privFlagBtns;                                    //clickable dimensions for these buttons
    public int numClickBools;
    //array of priv buttons to be cleared next frame - should always be empty except when buttons need to be cleared
    protected ArrayList<Integer> privBtnsToClear;
    
    
    //edit circle quantities for visual cues when grab and smoothen
    public static final int[] editCrcFillClrs = new int[] {DancingBalls.gui_FaintMagenta, DancingBalls.gui_FaintGreen};            
    public static final float[] editCrcRads = new float[] {20.0f,40.0f};            
    public static final float[] editCrcMods = new float[] {1f,2f};            
    public final myPoint[] editCrcCtrs = new myPoint[] {new myPoint(0,0,0),new myPoint(0,0,0)};            
    public float[] editCrcCurRads = new float[] {0,0};    
    
    //UI objects in this window
    //GUI Objects
    public myGUIObj[] guiObjs_Numeric;    
    public int msClkObj, msOvrObj;                                                //myGUIObj object that was clicked on  - for modification, object mouse moved over
    public float[] uiClkCoords;                                                //subregion of window where UI objects may be found
    public static final double uiWidthMult = 9;                            //multipler of size of label for width of UI components, when aligning components horizontally
    
    public double[][] guiMinMaxModVals;                    //min max mod values
    public double[] guiStVals;                            //starting values
    public String[] guiObjNames;                            //display labels for UI components    
    //idx 0 is treat as int, idx 1 is obj has list vals, idx 2 is object gets sent to windows
    public boolean[][] guiBoolVals;                        //array of UI flags for UI objects

    
    //offset to bottom of custom window menu 
    protected float custMenuOffset;
    //box holding x,y,w,h values of black rectangle to form around menu for display variables on right side of screen, if present
    private float[] UIRtSideRectBox;    

    //drawn trajectory
    public myDrawnSmplTraj tmpDrawnTraj;                        //currently drawn curve and all handling code - send to instanced owning screen

    //all trajectories in this particular display window - String key is unique identifier for what component trajectory is connected to
    public TreeMap<Integer,TreeMap<String,ArrayList<myDrawnSmplTraj>>> drwnTrajMap;                
    
    //ara to hold uniquely-identifying strings for each trajectory-receiving component
    public String[] trajNameAra;        
    
    public int numSubScrInWin = 2;                                    //# of subscreens in a window.  will generally be 1, but with sequencer will have at least 2 (piano roll and score view)
    public int[] numTrajInSubScr;                                    //# of trajectories available for each sub screen
    public static final int 
                traj1IDX = 0,
                traj2IDX = 1;
    
    public int curDrnTrajScrIDX;                                    //currently used/shown drawn trajectory - 1st idx (which screen)
    public int curTrajAraIDX;                                        //currently used/shown drawn trajectory - 2nd idx (which staff trajectory applies to)

    public int[][] drawTrajBoxFillClrs;
    
    ///////////
    //display and camera related variables - managed per window
    public static final float TWO_PI =(float) (Math.PI*2.0f), HALF_PI =(float) (Math.PI/2.0f);
    protected float dz=0, rx=(float) (-0.06f*TWO_PI), ry=-0.04f*TWO_PI;        // distance to camera. Manipulated with wheel or when,view angles manipulated when space pressed but not mouse    
    public final float camInitialDist = -200,        //initial distance camera is from scene - needs to be negative
            camInitRy = ry,
            camInitRx = rx;

    protected myVector focusTar;                            //target of focus - used in translate to set where the camera is looking - allow for modification
    protected myVector sceneFcsVal;                            //set this value  to be default target of focus    
    protected myPoint sceneOriginVal;                            //set this value to be different display center translations -to be used to calculate mouse offset in world for pick
    
    //to control how much is shown in the window - if stuff extends off the screen and for 2d window
    public myScrollBars[] scbrs;
    
    private final int[] trueBtnClr = new int[]{220,255,220,255}, falseBtnClr = new int[]{255,215,215,255};
    
    public Base_DispWindow(DancingBalls _p, String _n, int _flagIdx, int[] fc,  int[] sc, float[] rd, float[] rdClosed, String _winTxt, boolean _canDrawTraj) {
        pa=_p;
        ID = winCnt++;
        name = _n;
        winText = _winTxt;
        pFlagIdx = _flagIdx;
        initClrDims( fc, sc, rd, rdClosed);
        trajFillClrCnst = DancingBalls.gui_Black;        //override this in the ctor of the instancing window class
        trajStrkClrCnst = DancingBalls.gui_Black;
        
        msClkObj = -1;//    lastTrajIDX = -1; //lastPBEQueryPlayTime = 0;    
        msOvrObj = -1;
//        stAnimTime=0;
//        lastAnimTime=0;
    }    
    
    public void initThisWin(boolean _canDrawTraj, boolean _trajIsFlat, boolean _isMenu){
        initTmpTrajStuff(_trajIsFlat);    
        initFlags();    
        setFlags(canDrawTraj, _canDrawTraj);
        setFlags(trajPointsAreFlat, _trajIsFlat);
        //setFlags(closeable, true);
        setFlags(drawMseEdge,false);
        if(!_isMenu){
            initUIBox();                //set up ui click region to be in sidebar menu below menu's entries - do not do here for sidebar
        }
        curTrajAraIDX = 0;        
        setupGUIObjsAras();                //setup all ui objects and record final y value in sidebar menu for UI Objects in this window
        
        privBtnsToClear = new ArrayList<Integer>();
        initAllUIButtons();
        initMe();
        
        initRtSideMenuBox();
        setClosedBox();
        mseClickCrnr = new float[2];        //this is offset for click to check buttons in x and y - since buttons for all menus will be in menubar, this should be the upper left corner of menubar - upper left corner of rect 
        mseClickCrnr[0] = 0;
        mseClickCrnr[1] = 0;        
        if(getFlags(hasScrollBars)){scbrs = new myScrollBars[numSubScrInWin];    for(int i =0; i<numSubScrInWin;++i){scbrs[i] = new myScrollBars(pa, this);}}
    }//initThisWin
    
    private void initRtSideMenuBox() {
        //initialize right side info display window
        float boxWidth = 1.2f*rectDim[0];
        UIRtSideRectBox = new float[] {rectDim[2]-boxWidth,0,boxWidth, rectDim[3]};        
    }    
    //final initialization stuff, after window made, but necessary to make sure window displays correctly
    public void finalInit(boolean thisIs3D, boolean viewCanChange, myPoint _ctr, myVector _baseFcs) {
        setFlags(is3DWin, thisIs3D);
        setFlags(canChgView, viewCanChange);
        sceneFcsVal = new myVector(_baseFcs);
        sceneOriginVal = new myPoint(_ctr);
        focusTar = new myVector(_baseFcs);        
    }
    
    //init fill and stroke colors and dims of rectangular area open and closed - only called from ctor
    private void initClrDims(int[] fc,  int[] sc, float[] rd, float[] rdClosed) {
        fillClr = new int[4];rtSideUIFillClr= new int[4]; rtSideUIStrkClr= new int[4]; strkClr = new int[4];     
        rectDim = new float[4];    rectDimClosed = new float[4]; closeBox = new float[4]; uiClkCoords = new float[4];
        for(int i =0;i<4;++i){
            fillClr[i] = fc[i];strkClr[i]=sc[i];
            rtSideUIFillClr[i] = fc[i];rtSideUIStrkClr[i]=sc[i];            
            rectDim[i]=rd[i];rectDimClosed[i]=rdClosed[i];
        }                
    }//initClrDims    
    
    //set right side data display fill/stroke colors
    public void setRtSideUIBoxClrs(int[] fc,  int[] sc) {
        for(int i =0;i<4;++i){rtSideUIFillClr[i] = fc[i];rtSideUIStrkClr[i]=sc[i];}                
    }
    
    protected void initTmpTrajStuff(boolean _trajIsFlat){
        tmpDrawnTraj= new myDrawnSmplTraj(pa,this,topOffY,trajFillClrCnst, trajStrkClrCnst, _trajIsFlat, !_trajIsFlat);
        curDrnTrajScrIDX = 0;
    }    
    //initialize traj-specific stuff for this window
    protected void initTrajStructs(){
        drwnTrajMap = new TreeMap<Integer,TreeMap<String,ArrayList<myDrawnSmplTraj>>>();
        TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTrajMap;
        for(int scr =0;scr<numSubScrInWin; ++scr){
            tmpTrajMap = new TreeMap<String,ArrayList<myDrawnSmplTraj>>();
            for(int traj =0; traj<numTrajInSubScr[scr]; ++traj){
                tmpTrajMap.put(getTrajAraKeyStr(traj), new ArrayList<myDrawnSmplTraj>());            
            }    
            drwnTrajMap.put(scr, tmpTrajMap);
        }        
    }    
    //build UI clickable region
    protected void setUIClkCoords(float x1, float y1, float x2, float y2){uiClkCoords[0] = x1;uiClkCoords[1] = y1;uiClkCoords[2] = x2; uiClkCoords[3] = y2;}
    protected void setUIClkCoords(float[] cpy){    uiClkCoords[0] = cpy[0];uiClkCoords[1] = cpy[1];uiClkCoords[2] = cpy[2]; uiClkCoords[3] = cpy[3];}
    //public void initFlags(){dispFlags = new boolean[numDispFlags];for(int i =0; i<numDispFlags;++i){dispFlags[i]=false;}}        
    //base class flags init
    public void initFlags(){dispFlags = new int[1 + numDispFlags/32];for(int i =0; i<numDispFlags;++i){setFlags(i,false);}}        
    //child-class flag init
    protected void initPrivFlags(int numPrivFlags){privFlags = new int[1 + numPrivFlags/32]; for(int i = 0; i<numPrivFlags; ++i){setPrivFlags(i,false);}}
    //set up initial colors for sim specific flags for display
    protected void initPrivFlagColors(){
        privFlagColors = new int[truePrivFlagNames.length][4];
        for (int i = 0; i < privFlagColors.length; ++i) { privFlagColors[i] = new int[]{(int) pa.random(150),(int) pa.random(100),(int) pa.random(150), 255}; }            
    }
    
    //set up child class button rectangles
    protected void initUIBox(){        
        float [] menuUIClkCoords = pa.getUIRectVals(ID); 
        setUIClkCoords(menuUIClkCoords[0],menuUIClkCoords[3],menuUIClkCoords[2],menuUIClkCoords[3]);            
    }
    
    //calculate button length
    private static final float ltrLen = 5.0f;private static final int btnStep = 5;
    private float calcBtnLength(String tStr, String fStr){return btnStep * (int)(((PApplet.max(tStr.length(),fStr.length())+4) * ltrLen)/btnStep);}
    //set up child class button rectangles TODO
    //yDisp is displacement for button to be drawn
    protected void initPrivBtnRects(float yDisp, int numBtns){
        //pa.outStr2Scr("initPrivBtnRects in :"+ name + "st value for uiClkCoords[3]");
        float maxBtnLen = .95f * pa.menuWidth, halfBtnLen = .5f*maxBtnLen;
        //pa.pr("maxBtnLen : " + maxBtnLen);
        privFlagBtns = new float[numBtns][];
        this.uiClkCoords[3] += yOff;
        float oldBtnLen = 0;
        boolean lastBtnHalfStLine = false, startNewLine = true;
        for(int i=0; i<numBtns; ++i){                        //clickable button regions - as rect,so x,y,w,h - need to be in terms of sidebar menu 
            float btnLen = calcBtnLength(truePrivFlagNames[i].trim(),falsePrivFlagNames[i].trim());
            //either button of half length or full length.  if half length, might be changed to full length in next iteration.
            //pa.pr("initPrivBtnRects: i "+i+" len : " +btnLen+" cap 1: " + truePrivFlagNames[i].trim()+"|"+falsePrivFlagNames[i].trim());
            if(btnLen > halfBtnLen){//this button is bigger than halfsize - it needs to be made full size, and if last button was half size and start of line, make it full size as well
                btnLen = maxBtnLen;
                if(lastBtnHalfStLine){//make last button full size, and make button this button on another line
                    privFlagBtns[i-1][2] = maxBtnLen;
                    this.uiClkCoords[3] += yOff;
                }
                privFlagBtns[i]= new float[] {(float)(uiClkCoords[0]-xOff), (float) uiClkCoords[3], btnLen, yOff };                
                this.uiClkCoords[3] += yOff;
                startNewLine = true;
                lastBtnHalfStLine = false;
            } else {//button len should be half width unless this button started a new line
                btnLen = halfBtnLen;
                if(startNewLine){//button is starting new line
                    lastBtnHalfStLine = true;
                    privFlagBtns[i]= new float[] {(float)(uiClkCoords[0]-xOff), (float) uiClkCoords[3], btnLen, yOff };
                    startNewLine = false;
                } else {//should only get here if 2nd of two <1/2 width buttons in a row
                    lastBtnHalfStLine = false;
                    privFlagBtns[i]= new float[] {(float)(uiClkCoords[0]-xOff)+oldBtnLen, (float) uiClkCoords[3], btnLen, yOff };
                    this.uiClkCoords[3] += yOff;
                    startNewLine = true;                    
                }
            }            
            oldBtnLen = btnLen;
        }
        if(lastBtnHalfStLine){//set last button full length if starting new line
            privFlagBtns[numBtns-1][2] = maxBtnLen;            
        }
        this.uiClkCoords[3] += yOff;
        initPrivFlagColors();
    }//initPrivBtnRects
    //find index in flag name arrays of passed boolean IDX
    protected int getFlagAraIdxOfBool(int idx) {
        for(int i=0;i<privModFlgIdxs.length;++i) {if(idx == privModFlgIdxs[i]) {return i;}    }
        //not found
        return -1;
    }
    
    //set baseclass flags  //setFlags(showIDX, 
    public void setFlags(int idx, boolean val){
        int flIDX = idx/32, mask = 1<<(idx%32);
        dispFlags[flIDX] = (val ?  dispFlags[flIDX] | mask : dispFlags[flIDX] & ~mask);
        switch(idx){
            case showIDX             : {    
                setClosedBox();
                if(!val){//not showing window
                        closeMe();//specific instancing window implementation stuff to do when hidden/transitioning to another window (i.e. suspend stuff running outside draw loop, or release memory of unnecessary stuff)
                    } else {
                        showMe();//specific instance window functionality to do when window is shown
                    }
                break;}    
            case is3DWin             : {    break;}    
            case closeable             : {    break;}    
            case hasScrollBars         : {    break;}    
            case canDrawTraj         : {    break;}    
            case drawingTraj         : {    break;}    
            case editingTraj         : {    break;}    
            case showTrajEditCrc     : {    break;}    
            case smoothTraj         : {    break;}    
            case trajDecays         : {    break;}    
            case trajPointsAreFlat     : {    break;}    
            case procMouseMove         : {    break;}    
            case mouseSnapMove        : {    break;}    
            case uiObjMod            : {    break;}            
            case useRndBtnClrs        : { break;}
            case useCustCam            : { break;}
            case drawMseEdge        : { break;}
            case drawRightSideMenu    : { break;}
            case showRightSideMenu    : { break;}
            case clearPrivBtns        : { break;}
        }                
    }//setFlags
    
    //set the right side menu state for this window - if it is actually present, show it
    public void setRtSideInfoWinSt(boolean visible) {if(getFlags(drawRightSideMenu)) {setFlags(showRightSideMenu,visible);}}    
    
    //get baseclass flag
    public boolean getFlags(int idx){int bitLoc = 1<<(idx%32);return (dispFlags[idx/32] & bitLoc) == bitLoc;}    
    //check list of flags
    public boolean getAllFlags(int [] idxs){int bitLoc; for(int idx =0;idx<idxs.length;++idx){bitLoc = 1<<(idx%32);if ((dispFlags[idx/32] & bitLoc) != bitLoc){return false;}} return true;}
    public boolean getAnyFlags(int [] idxs){int bitLoc; for(int idx =0;idx<idxs.length;++idx){bitLoc = 1<<(idx%32);if ((dispFlags[idx/32] & bitLoc) == bitLoc){return true;}} return false;}
    
    //set/get child class flags
    public abstract void setPrivFlags(int idx, boolean val);
    public boolean getPrivFlags(int idx){int bitLoc = 1<<(idx%32);return (privFlags[idx/32] & bitLoc) == bitLoc;}    
    public boolean getAllPrivFlags(int [] idxs){int bitLoc; for(int idx =0;idx<idxs.length;++idx){bitLoc = 1<<(idx%32);if ((privFlags[idx/32] & bitLoc) != bitLoc){return false;}} return true;}
    public boolean getAnyPrivFlags(int [] idxs){int bitLoc; for(int idx =0;idx<idxs.length;++idx){bitLoc = 1<<(idx%32);if ((privFlags[idx/32] & bitLoc) == bitLoc){return true;}} return false;}
    //set a list of indexes in private flags array to be a specific value
    public void setAllPrivFlags(int[] idxs, boolean val) { for(int idx =0;idx<idxs.length;++idx) {setPrivFlags(idxs[idx],val);}}
    

    //for adding/deleting a screen programatically (loading a song) TODO
    //rebuild arrays of start locs whenever trajectory maps/arrays have changed - passed key is value modded in drwnTrajMap, 
    //modVal is if this is a deleted screen's map(0), a new map (new screen) at this location (1), or a modified map (added or deleted trajectory) (2)
    protected void rbldTrnsprtAras(int modScrKey, int modVal){
        if(modVal == -1){return;}//denotes no mod taken place
        int tmpNumSubScrInWin = drwnTrajMap.size();
        int [] tmpNumTrajInSubScr = new int[tmpNumSubScrInWin];
        float [] tmpVsblStLoc = new float[tmpNumSubScrInWin];
        int [] tmpSeqVisStTime = new int[tmpNumSubScrInWin];
        if(modVal == 0){            //deleted a screen's map
            if(tmpNumSubScrInWin != (numSubScrInWin -1)){pa.outStr2Scr("Error in rbldTrnsprtAras : screen traj map not removed at idx : " + modScrKey); return;}
            for(int i =0; i< numSubScrInWin; ++i){                    
            }            
            
        } else if (modVal == 1){    //added a new screen, with a new map            
        } else if (modVal == 2){    //modified an existing map (new or removed traj ara)            
        }
        numSubScrInWin = tmpNumSubScrInWin;
        numTrajInSubScr = tmpNumTrajInSubScr;
    }//rbldTrnsprtAras
    
    //for adding/deleting a screen programatically (loading a song) TODO
    //add or delete a new map of treemaps (if trajAraKey == "" or null), or a new map of traj arrays to existing key map
    protected void modTrajStructs(int scrKey, String trajAraKey, boolean del){
        int modMthd = -1;
        if(del){//delete a screen's worth of traj arrays, or a single traj array from a screen 
            if((trajAraKey == null) || (trajAraKey == "") ){        //delete screen map                
                TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTrajMap = drwnTrajMap.remove(scrKey);
                if(null != tmpTrajMap){            pa.outStr2Scr("Screen trajectory map removed for scr : " + scrKey);                modMthd = 0;}
                else {                            pa.outStr2Scr("Error : Screen trajectory map not found for scr : " + scrKey);     modMthd = -1; }
            } else {                                                //delete a submap within a screen
                modMthd = 2;                    //modifying existing map at this location
                TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTrajMap = drwnTrajMap.get(scrKey);
                if(null == tmpTrajMap){pa.outStr2Scr("Error : Screen trajectory map not found for scr : " + scrKey + " when trying to remove arraylist : "+trajAraKey); modMthd = -1;}
                else { 
                    ArrayList<myDrawnSmplTraj> tmpTrajAra = drwnTrajMap.get(scrKey).remove(trajAraKey);modMthd = 2;
                    if(null == tmpTrajAra){pa.outStr2Scr("Error : attempting to remove a trajectory array from a screen but trajAra not found. scr : " + scrKey + " | trajAraKey : "+trajAraKey);modMthd = -1; }
                }
            }             
        } else {                                                    //add
            TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTrajMap = drwnTrajMap.get(scrKey);
            if((trajAraKey == null) || (trajAraKey == "") ){        //add map of maps - added a new screen                
                if(null != tmpTrajMap){pa.outStr2Scr("Error : attempting to add a new drwnTrajMap where one exists. scr : " + scrKey);modMthd = -1; }
                else {tmpTrajMap = new TreeMap<String,ArrayList<myDrawnSmplTraj>>();    drwnTrajMap.put(scrKey, tmpTrajMap);modMthd = 1;}
            } else {                                                //add new map of trajs to existing screen's map
                ArrayList<myDrawnSmplTraj> tmpTrajAra = drwnTrajMap.get(scrKey).get(trajAraKey);    
                if(null == tmpTrajMap){pa.outStr2Scr("Error : attempting to add a new trajectory array to a screen that doesn't exist. scr : " + scrKey + " | trajAraKey : "+trajAraKey); modMthd = -1; }
                else if(null != tmpTrajAra){pa.outStr2Scr("Error : attempting to add a new trajectory array to a screen where one already exists. scr : " + scrKey + " | trajAraKey : "+trajAraKey);modMthd = -1; }
                else {    tmpTrajAra = new ArrayList<myDrawnSmplTraj>();            tmpTrajMap.put(trajAraKey, tmpTrajAra);    drwnTrajMap.put(scrKey, tmpTrajMap);modMthd = 2;}
            }            
        }//if del else add
        //rebuild arrays of start loc
        rbldTrnsprtAras(scrKey, modMthd);
    }
    
    //this will set the height of the rectangle enclosing this window - this will be called when a window pushes up or pulls down this window
    //this resizes any drawn trajectories in this window, and calls the instance class's code for resizing
    public void setRectDimsY(float height){
        float oldVal = getFlags(showIDX) ? rectDim[3] : rectDimClosed[3];
        rectDim[3] = height;
        rectDimClosed[3] = height;
        float scale  = height/oldVal;            //scale of modification - rescale the size and location of all components of this window by this
        //resize drawn all trajectories
        TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTreeMap = drwnTrajMap.get(this.curDrnTrajScrIDX);
        if((tmpTreeMap != null) && (tmpTreeMap.size() != 0)) {
            for(int i =0; i<tmpTreeMap.size(); ++i){
                ArrayList<myDrawnSmplTraj> tmpAra = tmpTreeMap.get(getTrajAraKeyStr(i));            
                if(null!=tmpAra){    for(int j =0; j<tmpAra.size();++j){        tmpAra.get(j).reCalcCntlPoints(scale);    }    }
            }    
        }
        if(getFlags(hasScrollBars)){for(int i =0; i<scbrs.length;++i){scbrs[i].setSize();}}
        resizeMe(scale);
    }
//    //resize and relocate UI objects in this window for resizing window
//    public void resizeUIRegion(float scaleY, int numGUIObjs){
//        //re-size where UI objects should be drawn - vertical scale only currently
//        double [] curUIVals = new double[this.guiStVals.length];
//        for(int i=0;i<curUIVals.length;++i){    curUIVals[i] = guiObjs_Numeric[i].getVal();    }
//        
//        uiClkCoords[1]=calcDBLOffsetScale(uiClkCoords[1],scaleY,topOffY);
//        uiClkCoords[3]=calcDBLOffsetScale(uiClkCoords[3],scaleY,topOffY);
//        //setUIClkCoords(uiClkCoords[0],uiClkCoords[1],uiClkCoords[2],uiClkCoords[3]);
//        if(0!=numGUIObjs){
//            buildGUIObjs(guiObjNames,curUIVals,guiMinMaxModVals,guiBoolVals,new double[]{xOff,yOff});
//        }
//    }
    
    //build myGUIObj objects for interaction - call from setupMenuClkRegions of window, 
    //uiClkCoords needs to be derived before this is called by child class - maxY val(for vertical stack) or maxX val(for horizontal stack) will be derived here
    protected void buildGUIObjs(String[] guiObjNames, double[] guiStVals, double[][] guiMinMaxModVals, boolean[][] guiBoolVals, double[] off){
        //myGUIObj tmp; 
//        if(getFlags(uiObjsAreVert]){        //vertical stack of UI components - clickable region x is unchanged, y changes with # of objects
        float stClkY = uiClkCoords[1];
        for(int i =0; i< guiObjs_Numeric.length; ++i){
            guiObjs_Numeric[i] = buildGUIObj(i,guiObjNames[i],guiStVals[i], guiMinMaxModVals[i], guiBoolVals[i], new double[]{uiClkCoords[0], stClkY, uiClkCoords[2], stClkY+yOff},off);
            stClkY += yOff;
        }
        uiClkCoords[3] = stClkY;    
//        } else {            //horizontal row of UI components - clickable region y is unchanged, x changes with # of objects
//            double stClkX = uiClkCoords[0];
//            double UICompWidth;
//            for(int i =0; i< guiObjs_Numeric.length; ++i){
//                UICompWidth = (uiWidthMult + (guiBoolVals[i][1] ? 1 : 0)) * guiObjNames[i].length();
//                guiObjs_Numeric[i] = buildGUIObj(i,guiObjNames[i],guiStVals[i], guiMinMaxModVals[i], guiBoolVals[i], new double[]{stClkX, uiClkCoords[1], stClkX+UICompWidth , uiClkCoords[3]},off);
//                stClkX += UICompWidth;
//            }
//            uiClkCoords[2] = stClkX;    
//        }
    }//
    
    protected myGUIObj buildGUIObj(int i, String guiObjName, double guiStVal, double[] guiMinMaxModVals, boolean[] guiBoolVals, double[] xyDims, double[] off){
        return new myGUIObj(pa, this,i, guiObjName, xyDims[0], xyDims[1], xyDims[2], xyDims[3], guiMinMaxModVals, guiStVal, guiBoolVals, off);
    }    
    
    //this returns a formatted string holding the UI data
    protected String getStrFromUIObj(int idx){
        StringBuilder sb = new StringBuilder(400);
        sb.append("ui_idx: ");
        sb.append(idx);
        sb.append(" |name: ");
        sb.append(guiObjs_Numeric[idx].name);
        sb.append(" |value: ");
        sb.append(guiObjs_Numeric[idx].getVal());
        sb.append(" |flags: ");
        for(int i =0;i<myGUIObj.numFlags; ++i){
            sb.append(" ");
            sb.append((guiObjs_Numeric[idx].getFlags(i) ? "true" : "false"));
        }
        return sb.toString().trim();        
        
    }//getStrFromUIObj
        
    //this sets the value of a gui object from the data held in a string
    protected void setValFromFileStr(String str){
        String[] toks = str.trim().split("\\|");
        //window has no data values to load
        if(toks.length==0){return;}
        int uiIdx = Integer.parseInt(toks[0].split("\\s")[1].trim());
        //String name = toks[3];
        double uiVal = Double.parseDouble(toks[2].split("\\s")[1].trim());    
        guiObjs_Numeric[uiIdx].setVal(uiVal);
        for(int i =0;i<myGUIObj.numFlags; ++i){
            guiObjs_Numeric[uiIdx].setFlags(i, Boolean.parseBoolean(toks[3].split("\\s")[i].trim()));
        }    
        setUIWinVals(uiIdx);//update window's values with UI construct's values
    }//setValFromFileStr

    //take loaded params and process - stIdx will be idx of this window's name - move forward 1 to start on objects
    protected void hndlFileLoad(String[] vals, int[] stIdx){
        ++stIdx[0];
        //set values for ui sliders
        while(!vals[stIdx[0]].contains(name + "_custUIComps")){
            if(vals[stIdx[0]].trim() != ""){    setValFromFileStr(vals[stIdx[0]]);    }
            ++stIdx[0];
        }
        ++stIdx[0];        
        //handle window-specific UI components, if any
        this.hndlFileLoadIndiv(vals, stIdx);
    }//hndlFileLoad
    
    //accumulate array of params to save
    protected ArrayList<String> hndlFileSave(){
        ArrayList<String> res = new ArrayList<String>();
        res.add(name);
        for(int i=0;i<guiObjs_Numeric.length;++i){    res.add(getStrFromUIObj(i));}        
        //bound for custom components
        res.add(name + "_custUIComps");
        //call indiv handler
        res.addAll(hndlFileSaveIndiv());
        //add blank space
        res.add("");
        return res;
    }//
    
    public float calcOffsetScale(double val, float sc, float off){float res =(float)val - off; res *=sc; return res+=off;}
    public double calcDBLOffsetScale(double val, float sc, double off){double res = val - off; res *=sc; return res+=off;}
    //returns passed current passed dimension from either rectDim or rectDimClosed
    public float getRectDim(int idx){return ( getFlags(showIDX) ? rectDim[idx] : rectDimClosed[idx]);    }

    public void setClosedBox(){
        if( getFlags(showIDX)){    closeBox[0] = rectDim[0]+rectDim[2]-clkBxDim;closeBox[1] = rectDim[1];    closeBox[2] = clkBxDim;    closeBox[3] = clkBxDim;} 
        else {                    closeBox[0] = rectDimClosed[0]+rectDimClosed[2]-clkBxDim;closeBox[1] = rectDimClosed[1];    closeBox[2] = clkBxDim;    closeBox[3] = clkBxDim;}
    }    
    
    //set up initial trajectories - 2d array, 1 per UI Page, 1 per modifiable construct within page.
    public void initDrwnTrajs(){
        numSubScrInWin = 2;                        //# of subscreens in a window.  will generally be 1, but with sequencer will have at least 2 (piano roll and score view)
        int numTrajPerScr = 10;
        trajNameAra = new String[numTrajPerScr];
        numTrajInSubScr = new int[]{numTrajPerScr,numTrajPerScr};    
        for(int i =0; i<numTrajPerScr; ++i){
            trajNameAra[i] = "traj_"+(i+1);
        }
        initTrajStructs();        
        //setupGUIObjsAras();                    //rebuild UI object stuff
        initDrwnTrajIndiv();                
    }
    
    //displays point with a name
    protected void showKeyPt(myPoint a, String s, float rad){    pa.show(a,rad, s, new myVector(10,-5,0), DancingBalls.gui_Cyan, getFlags(trajPointsAreFlat));    }    
    //draw a series of strings in a column
    protected void dispMenuTxtLat(String txt, int[] clrAra, boolean showSphere){
        pa.setFill(clrAra, 255); 
        pa.translate(xOff*.5f,yOff*.5f);
        if(showSphere){pa.setStroke(clrAra, 255);        pa.sphere(5);    } 
        else {    pa.noStroke();        }
        pa.translate(-xOff*.5f,yOff*.5f);
        pa.text(""+txt,xOff,-yOff*.25f);    
    }
    protected void dispBoolStFlag(String txt, int[] clrAra, boolean state, float stMult){
        if(state){
            pa.setFill(clrAra, 255); 
            pa.setStroke(clrAra, 255);
        } else {
            pa.setColorValFill(DancingBalls.gui_DarkGray); 
            pa.noStroke();    
        }
        pa.sphere(5);
        //pa.text(""+txt,-xOff,yOff*.8f);    
        pa.text(""+txt,stMult*txt.length(),yOff*.8f);    
    }
    
    //draw a series of strings in a row
    protected void dispBttnAtLoc(String txt, float[] loc, int[] clrAra){
        pa.setFill(clrAra);
        pa.setColorValStroke(DancingBalls.gui_Black);
        pa.rect(loc);        
        pa.setColorValFill(DancingBalls.gui_Black);
        //pa.translate(-xOff*.5f,-yOff*.5f);
        pa.text(""+txt,loc[0] + (txt.length() * .3f),loc[1]+loc[3]*.75f);
        //pa.translate(width, 0);
    }
    
    //whether or not to draw the mouse reticle/rgb(xyz) projection/edge to eye
    public boolean chkDrawMseRet(){        return getFlags(drawMseEdge);    }
    
    protected void drawTraj(float animTimeMod){
        pa.pushMatrix();pa.pushStyle();    
        if(null != tmpDrawnTraj){tmpDrawnTraj.drawMe(animTimeMod);}
        TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTreeMap = drwnTrajMap.get(this.curDrnTrajScrIDX);
        if((tmpTreeMap != null) && (tmpTreeMap.size() != 0)) {
            for(int i =0; i<tmpTreeMap.size(); ++i){
                ArrayList<myDrawnSmplTraj> tmpAra = tmpTreeMap.get(getTrajAraKeyStr(i));            
                if(null!=tmpAra){    for(int j =0; j<tmpAra.size();++j){tmpAra.get(j).drawMe(animTimeMod);}}
            }    
        }
        pa.popStyle();pa.popMatrix();        
    }
    
    //draw ui objects
    public void drawGUIObjs(){    
        pa.pushMatrix();pa.pushStyle();    
        for(int i =0; i<guiObjs_Numeric.length; ++i){guiObjs_Numeric[i].draw();}
        pa.popStyle();pa.popMatrix();
    }
    
    //draw all boolean-based buttons for this window
    public void drawClickableBooleans() {    
        pa.pushMatrix();pa.pushStyle();    
        pa.setColorValFill(DancingBalls.gui_Black);
        if(getFlags(useRndBtnClrs)){
            for(int i =0; i<privModFlgIdxs.length; ++i){//prlFlagRects dispBttnAtLoc(String txt, float[] loc, int[] clrAra)
                if(getPrivFlags(privModFlgIdxs[i]) ){                                    dispBttnAtLoc(truePrivFlagNames[i],privFlagBtns[i],privFlagColors[i]);            }
                else {    if(truePrivFlagNames[i].equals(falsePrivFlagNames[i])) {    dispBttnAtLoc(truePrivFlagNames[i],privFlagBtns[i],new int[]{180,180,180, 255});}    
                        else {                                                        dispBttnAtLoc(falsePrivFlagNames[i],privFlagBtns[i],new int[]{0,255-privFlagColors[i][1],255-privFlagColors[i][2], 255});}        
                }
            }        
        } else {
            for(int i =0; i<privModFlgIdxs.length; ++i){//prlFlagRects dispBttnAtLoc(String txt, float[] loc, int[] clrAra)
                if(getPrivFlags(privModFlgIdxs[i]) ){                                dispBttnAtLoc(truePrivFlagNames[i],privFlagBtns[i],trueBtnClr);            }
                else {                                                                dispBttnAtLoc(falsePrivFlagNames[i],privFlagBtns[i],falseBtnClr);    }
            }        

        }
        pa.popStyle();pa.popMatrix();
    }//drawClickableBooleans
    
    //draw any custom menu objects for sidebar menu
    public abstract void drawCustMenuObjs();
    
    public abstract void initAllUIButtons();
    
    //////////////////////
    //camera stuff
    
    //sets camera view
    public void setInitCamView(){//also sets idx in scene focus and center arrays
        rx = camInitRx;
        ry = camInitRy;
        dz = camInitialDist;        
    }//setCamView()    

    public void setCamera(float[] camVals){
        if(getFlags(useCustCam)){setCameraIndiv (camVals);}//individual window camera handling
        else {
            pa.camera(camVals[0],camVals[1],camVals[2],camVals[3],camVals[4],camVals[5],camVals[6],camVals[7],camVals[8]);      
            //if(this.flags[this.debugMode]){outStr2Scr("rx :  " + rx + " ry : " + ry + " dz : " + dz);}
            // puts origin of all drawn objects at screen center and moves forward/away by dz
            pa.translate(camVals[0],camVals[1],(float)dz); 
            setCamOrient();    
        }
    }//setCamera

    //used to handle camera location/motion
    public void setCamOrient(){pa.rotateX(rx);pa.rotateY(ry); pa.rotateX(HALF_PI);        }//sets the rx, ry, pi/2 orientation of the camera eye    
    //used to draw text on screen without changing mode - reverses camera orientation setting
    public void unSetCamOrient(){pa.rotateX(-HALF_PI); pa.rotateY(-ry);   pa.rotateX(-rx); }//reverses the rx,ry,pi/2 orientation of the camera eye - paints on screen and is unaffected by camera movement
    //return display string for camera location
    public String getCamDisp() {return " camera rx :  " + rx + " ry : " + ry + " dz : " + dz ; }
    
    //initial draw stuff
    public void drawSetupWin(float[] camVals) {
        setCamera(camVals);
        pa.translate(focusTar.x,focusTar.y,focusTar.z);
    }
    
    //draw box to hide window
    protected void drawMouseBox(){
        if( getFlags(showIDX)){
            pa.setColorValFill(DancingBalls.gui_LightGreen );
            pa.rect(closeBox);
            pa.setFill(strkClr);
            pa.text("Close" , closeBox[0]-35, closeBox[1]+10);
        } else {
            pa.setColorValFill(DancingBalls.gui_DarkRed);
            pa.rect(closeBox);
            pa.setFill(strkClr);
            pa.text("Open", closeBox[0]-35, closeBox[1]+10);            
        }
    }
    public void drawSmall(){
        pa.pushMatrix();                pa.pushStyle();    
        //pa.outStr2Scr("Hitting hint code draw small");
        pa.hint(PConstants.DISABLE_DEPTH_TEST);
        pa.noLights();        
        pa.setStroke(strkClr);
        pa.setFill(fillClr);
        //main window drawing
        pa.rect(rectDimClosed);        
        pa.setFill(strkClr);
        if(winText.trim() != ""){
            pa.text(winText.split(" ")[0], rectDimClosed[0]+10, rectDimClosed[1]+25);
        }        
        //close box drawing
        if(getFlags(closeable)){drawMouseBox();}
        pa.hint(PConstants.ENABLE_DEPTH_TEST);
        pa.popStyle();pa.popMatrix();        
    }
    
    public void drawHeader(float modAmtMillis){
        if(!getFlags(showIDX)){return;}
        pa.pushMatrix();                pa.pushStyle();            
        //pa.outStr2Scr("Hitting hint code drawHeader");
        pa.hint(PConstants.DISABLE_DEPTH_TEST);
        pa.noLights();        
        pa.setStroke(strkClr);
        pa.setFill(strkClr);
        if(winText.trim() != ""){    pa.ml_text(winText,  rectDim[0]+10,  rectDim[1]+10);}
        if(getFlags(canDrawTraj)){    drawNotifications();    }                //if this window accepts a drawn trajectory, then allow it to be displayed
        if(getFlags(closeable)){drawMouseBox();}
        if(getFlags(hasScrollBars)){scbrs[curDrnTrajScrIDX].drawMe();}
        //draw rightSideMenu stuff, if this window supports it
        drawOnScreenStruff(modAmtMillis);    
        pa.lights();    
        pa.hint(PConstants.ENABLE_DEPTH_TEST);
        pa.popStyle();pa.popMatrix();    
        //last thing per draw - clear btns that have been set to clear after 1 frame of display
        if (getFlags(clearPrivBtns)) {clearAllPrivBtns();setFlags(clearPrivBtns,false);}
        if (privBtnsToClear.size() > 0){setFlags(clearPrivBtns, true);    }        
    }//drawHeader
    
    //draw right side "menu" used to display simualtion/calculation variables and results
    private void drawOnScreenStruff(float modAmtMillis) {
        pa.pushMatrix();pa.pushStyle();
        //move to upper right corner of sidebar menu - cannot draw over leftside menu, use drawCustMenuObjs() instead to put UI objects there
        //this side window is for information display
        pa.translate(rectDim[0],0,0);        
        //draw onscreen stuff for main window over center of window
        drawOnScreenStuffPriv(modAmtMillis);
        //draw right side info display if relelvant
        if(getFlags(drawRightSideMenu)) {
            pa.setFill(rtSideUIFillClr);//transparent black
            if(getFlags(showRightSideMenu)) {    
                //draw data in right side menu box
                pa.rect(UIRtSideRectBox);
                pa.translate(UIRtSideRectBox[0]+5,UIRtSideRectBox[1]+yOff-4,0);
                pa.setFill(new int[] {255,255,255,255});
    
                 //instancing class implements this function
                drawRightSideInfoBar(modAmtMillis); 
            } else {
                //shows narrow rectangular reminder that window is there
                pa.translate(rectDim[2]-20,0,0);
                pa.rect(new float[] {0,0,20,rectDim[3]});
            }
        }
        pa.popStyle();pa.popMatrix();            
    }//drawRtSideInfoBar
    
    public void draw3D(float modAmtMillis){
        if(!getFlags(showIDX)){return;}
        //stAnimTime = pa.millis();
        float animTimeMod = (modAmtMillis/1000.0f);//in seconds
        //lastAnimTime = pa.millis();
        pa.pushMatrix();                pa.pushStyle();            
        pa.setFill(fillClr);
        pa.setStroke(strkClr);
        //if(getFlags(closeable)){drawMouseBox();}
        drawMe(animTimeMod);            //call instance class's draw
        if(getFlags(canDrawTraj)){
            pa.pushMatrix();                pa.pushStyle();    
            drawTraj3D(animTimeMod, myPoint._add(sceneOriginVal,focusTar));            
            pa.popStyle();pa.popMatrix();
            if(getFlags(showTrajEditCrc)){drawClkCircle();}
        }                //if this window accepts a drawn trajectory, then allow it to be displayed
        pa.popStyle();pa.popMatrix();        
    }//draw3D
    
    public void drawTraj3D(float animTimeMod,myPoint trans){
        pa.outStr2Scr("Base_DispWindow.drawTraj3D() : I should be overridden in 3d instancing class", true);
//        pa.pushMatrix();pa.pushStyle();    
//        if(null != tmpDrawnTraj){tmpDrawnTraj.drawMe(animTimeMod);}
//        TreeMap<String,ArrayList<myDrawnNoteTraj>> tmpTreeMap = drwnTrajMap.get(this.curDrnTrajScrIDX);
//        if((tmpTreeMap != null) && (tmpTreeMap.size() != 0)) {
//            for(int i =0; i<tmpTreeMap.size(); ++i){
//                ArrayList<myDrawnNoteTraj> tmpAra = tmpTreeMap.get(getTrajAraKeyStr(i));            
//                if(null!=tmpAra){    for(int j =0; j<tmpAra.size();++j){tmpAra.get(j).drawMe(animTimeMod);}}
//            }    
//        }
//        pa.popStyle();pa.popMatrix();        
    }//drawTraj3D
    
    public void draw2D(float modAmtMillis){
        if(!getFlags(showIDX)){drawSmall();return;}
        //stAnimTime = pa.millis();
        float animTimeMod = (modAmtMillis/1000.0f);
        //lastAnimTime = pa.millis();
        pa.pushMatrix();                pa.pushStyle();    
        //pa.outStr2Scr("Hitting hint code draw2D");
        pa.hint(PConstants.DISABLE_DEPTH_TEST);
        pa.setStroke(strkClr);
        pa.setFill(fillClr);
        //main window drawing
        pa.rect(rectDim);
        //close box drawing
        drawMe(animTimeMod);            //call instance class's draw
        if(getFlags(canDrawTraj)){
            drawTraj(animTimeMod);            
            if(getFlags(showTrajEditCrc)){drawClkCircle();}
        }                //if this window accepts a drawn trajectory, then allow it to be displayed
        pa.hint(PConstants.ENABLE_DEPTH_TEST);
        pa.popStyle();pa.popMatrix();
    }
    
    protected void drawNotifications(){        
        //debug stuff
        pa.pushMatrix();                pa.pushStyle();
        pa.translate(rectDim[0]+20,rectDim[1]+rectDim[3]-70);
        dispMenuTxtLat("Drawing curve", pa.getClr((getFlags(drawingTraj) ? DancingBalls.gui_Green : DancingBalls.gui_Red)), true);
        //pa.show(new myPoint(0,0,0),4, "Drawing curve",new myVector(10,15,0),(getFlags(this.drawingTraj) ? pa.gui_Green : pa.gui_Red));
        //pa.translate(0,-30);
        dispMenuTxtLat("Editing curve", pa.getClr((getFlags(editingTraj) ? DancingBalls.gui_Green : DancingBalls.gui_Red)), true);
        //pa.show(new myPoint(0,0,0),4, "Editing curve",new myVector(10,15,0),(getFlags(this.editingTraj) ? pa.gui_Green : pa.gui_Red));
        pa.popStyle();pa.popMatrix();        
    }

    protected void drawClkCircle(){
        pa.pushMatrix();                pa.pushStyle();
        boolean doneDrawing = true;
        for(int i =0; i<editCrcFillClrs.length;++i){
            if(editCrcCurRads[i] <= 0){continue;}
            pa.setColorValFill(editCrcFillClrs[i]);
            pa.noStroke();
            pa.circle(editCrcCtrs[i],editCrcCurRads[i]);
            editCrcCurRads[i] -= editCrcMods[i];
            doneDrawing = false;
        }
        if(doneDrawing){setFlags(showTrajEditCrc, false);}
        pa.popStyle();pa.popMatrix();        
    }
    
    public void simulate(float modAmtMillis){
        boolean simDone = simMe(modAmtMillis);
        if(simDone) {endSim();}
    }//
    
    //if ending simulation, call this function
    private void endSim() {    pa.setFlags(pa.runSim, false);}//endSim
    
    //call after single draw - will clear window-based priv buttons that are momentary
    protected void clearAllPrivBtns() {
        if(privBtnsToClear.size() == 0) {return;}
        for (Integer idx : privBtnsToClear) {this.setPrivFlags(idx, false);}
        privBtnsToClear.clear();
    }//clearPrivBtns()
    //add a button to clear after next draw
    
    protected void addPrivSwitchToClear(int idx) {
        privBtnsToClear.add(idx);
    }
    
    protected boolean handleTrajClick(boolean keysToDrawClicked, myPoint mse){
        boolean mod = false;
        if(keysToDrawClicked){                    //drawing curve with click+alt - drawing on canvas
            //pa.outStr2Scr("Current trajectory key IDX " + curTrajAraIDX);
            startBuildDrawObj();    
            mod = true;
            //
        } else {
        //    pa.outStr2Scr("Current trajectory key IDX edit " + curTrajAraIDX);
            this.tmpDrawnTraj = findTraj(mse);                            //find closest trajectory to the mouse's click location
            
            if ((null != this.tmpDrawnTraj)  && (null != this.tmpDrawnTraj.drawnTraj)) {                    //alt key not pressed means we're possibly editing a curve, if it exists and if we click within "sight" of it, or moving endpoints
                //pa.outStr2Scr("Current trajectory ID " + tmpDrawnTraj.ID);
                mod = this.tmpDrawnTraj.startEditObj(mse);
            }
        }
        return mod;
    }//
    
    public myDrawnSmplTraj findTraj(myPoint mse){
        TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTreeMap = drwnTrajMap.get(this.curDrnTrajScrIDX);
        if((tmpTreeMap != null) && (tmpTreeMap.size() != 0)) {
            for(int i =0; i<tmpTreeMap.size(); ++i){
                ArrayList<myDrawnSmplTraj> tmpAra = tmpTreeMap.get(getTrajAraKeyStr(i));            
                if(null!=tmpAra){    for(int j =0; j<tmpAra.size();++j){    if(tmpAra.get(j).clickedMe(mse)){return tmpAra.get(j);}}}
            }    
        }
        return null;        
    }
    
    //stuff to do when shown/hidden
    public void setShow(boolean val){
        setFlags(showIDX,val);
        setClosedBox();
        if(!getFlags(showIDX)){//not showing window
            closeMe();//specific instancing window implementation stuff to do when hidden
        } else {
            showMe();//specific instance window functionality to do when window is shown
        }
    }
    
    protected void toggleWindowState(){
        //pa.outStr2Scr("Attempting to close window : " + this.name);
        setFlags(showIDX,!getFlags(showIDX));
        pa.setFlags(pFlagIdx, getFlags(showIDX));        //value has been changed above by close box
    }
    
    protected boolean checkClsBox(int mouseX, int mouseY){
        boolean res = false;
        if(pa.ptInRange(mouseX, mouseY, closeBox[0], closeBox[1], closeBox[0]+closeBox[2], closeBox[1]+closeBox[3])){toggleWindowState(); res = true;}                
        return res;        
    }
    //check if mouse location is in UI buttons, and handle button click if so
    protected boolean checkUIButtons(int mouseX, int mouseY){
        boolean mod = false;
        //keep checking -see if clicked in UI buttons (flag-based buttons)
        for(int i = 0;i<privFlagBtns.length;++i){//rectDim[0], rectDim[1]  mseClickCrnr
            mod = msePtInRect(mouseX, mouseY, privFlagBtns[i]); 
            //pa.outStr2Scr("Handle mouse click in window : "+ ID + " : (" + mouseX+","+mouseY+") : "+mod + ": btn rect : "+privFlagBtns[i][0]+","+privFlagBtns[i][1]+","+privFlagBtns[i][2]+","+privFlagBtns[i][3]);
            if (mod){ 
                setPrivFlags(privModFlgIdxs[i],!getPrivFlags(privModFlgIdxs[i])); 
                return mod;
            }            
        }
        return mod;
    }//checkUIButtons    
    
    //change view based on mouse click/drag behavior and whether we are moving or zooming
    //use delX for zoom
    public void handleViewChange(boolean doZoom, float delX, float delY ) {
        if(doZoom) {    dz-=delX;    } 
        else {            rx-=delX; ry+=delY;}         
    }//handleViewChange()
    
    protected myPoint getMsePoint(myPoint pt){return getFlags(Base_DispWindow.is3DWin) ? getMsePtAs3DPt((int)pt.x, (int)pt.y) : pt;}
    protected myPoint getMsePoint(int mouseX, int mouseY){return getFlags(Base_DispWindow.is3DWin) ? getMsePtAs3DPt(mouseX, mouseY) : pa.P(mouseX,mouseY,0);}
    public boolean handleMouseMove(int mouseX, int mouseY){
        if(!getFlags(showIDX)){return false;}
        if((getFlags(showIDX))&& (msePtInUIClckCoords(mouseX, mouseY))){//in clickable region for UI interaction
            for(int j=0; j<guiObjs_Numeric.length; ++j){if(guiObjs_Numeric[j].checkIn(mouseX, mouseY)){    msOvrObj=j;return true;    }}
        }
        myPoint mouseClickIn3D = pa.c.getMseLoc(sceneOriginVal);
        if(hndlMouseMoveIndiv(mouseX, mouseY, mouseClickIn3D)){return true;}
        msOvrObj = -1;
        return false;
    }//handleMouseMove
    
    public boolean msePtInRect(int x, int y, float[] r){return ((x > r[0])&&(x <= r[0]+r[2])&&(y > r[1])&&(y <= r[1]+r[3]));}    
    public boolean msePtInUIClckCoords(int x, int y){return ((x > uiClkCoords[0])&&(x <= uiClkCoords[2])&&(y > uiClkCoords[1])&&(y <= uiClkCoords[3]));}    

    public boolean handleMouseClick(int mouseX, int mouseY, int mseBtn){
        boolean mod = false;
        if((getFlags(showIDX))&& (msePtInUIClckCoords(mouseX, mouseY))){//in clickable region for UI interaction
            for(int j=0; j<guiObjs_Numeric.length; ++j){
                if(guiObjs_Numeric[j].checkIn(mouseX, mouseY)){    
                    if(pa.flags[pa.shiftKeyPressed]){//allows for click-mod
                        float mult = mseBtn * -2.0f + 1;    //+1 for left, -1 for right btn    
                        //pa.outStr2Scr("Mult : " + (mult *pa.clickValModMult()));
                        guiObjs_Numeric[j].clkModVal(mult * pa.clickValModMult());
                        setFlags(uiObjMod,true);
                    } //else {                                        //has drag mod
                    msClkObj=j;
                    //}
                    return true;    
                }
            }
        }            
        if(getFlags(closeable)){mod = checkClsBox(mouseX, mouseY);}                            //check if trying to close or open the window via click, if possible
        if(!getFlags(showIDX)){return mod;}
        if(!mod){
            myPoint mouseClickIn3D = pa.c.getMseLoc(sceneOriginVal);
            mod = hndlMouseClickIndiv(mouseX, mouseY,mouseClickIn3D, mseBtn);
        }            //if nothing triggered yet, then specific instancing window implementation stuff
        if((!mod) && (msePtInRect(mouseX, mouseY, this.rectDim)) && (getFlags(canDrawTraj))){ 
            myPoint pt =  getMsePoint(mouseX, mouseY);
            if(null==pt){return false;}
            mod = handleTrajClick(pa.flags[pa.altKeyPressed], pt);}            //click + alt for traj drawing : only allow drawing trajectory if it can be drawn and no other interaction has occurred
        return mod;
    }//handleMouseClick
    //vector for drag in 3D
    public boolean handleMouseDrag(int mouseX, int mouseY,int pmouseX, int pmouseY, myVector mseDragInWorld, int mseBtn){
        boolean mod = false;
        if(!getFlags(showIDX)){return mod;}
        //check if modding view
        if ((pa.flags[pa.shiftKeyPressed]) && getFlags(canChgView) && (msClkObj==-1)) {
            pa.setFlags(pa.modView,true);    
            if(mseBtn == 0){            handleViewChange(false,pa.msSclY*(mouseY-pmouseY), pa.msSclX*(mouseX-pmouseX));}    
            else if (mseBtn == 1) {        handleViewChange(true,(mouseY-pmouseY), 0);}    //moveZoom(mouseY-pmouseY);}//dz-=(
            return true;
        } else {//modify UI elements
        
            //any generic dragging stuff - need flag to determine if trajectory is being entered        
            //modify object that was clicked in by mouse motion
            if(msClkObj!=-1){    guiObjs_Numeric[msClkObj].modVal((mouseX-pmouseX)+(mouseY-pmouseY)*-(pa.flags[pa.shiftKeyPressed] ? 50.0f : 5.0f));setFlags(uiObjMod, true); return true;}        
            if(getFlags(drawingTraj)){         //if drawing trajectory has started, then process it
                //pa.outStr2Scr("drawing traj");
                myPoint pt =  getMsePoint(mouseX, mouseY);
                if(null==pt){return false;}
                this.tmpDrawnTraj.addPoint(pt);
                mod = true;
            }else if(getFlags(editingTraj)){        //if editing trajectory has started, then process it
                //pa.outStr2Scr("edit traj");    
                myPoint pt =  getMsePoint(mouseX, mouseY);
                if(null==pt){return false;}
                mod = this.tmpDrawnTraj.editTraj(mouseX, mouseY,pmouseX, pmouseY,pt,mseDragInWorld);
            }
            else {
                if((!pa.ptInRange(mouseX, mouseY, rectDim[0], rectDim[1], rectDim[0]+rectDim[2], rectDim[1]+rectDim[3]))){return false;}    //if not drawing or editing a trajectory, force all dragging to be within window rectangle
                //pa.outStr2Scr("before handle indiv drag traj for window : " + this.name);
                myPoint mouseClickIn3D = pa.c.getMseLoc(sceneOriginVal);
                mod = hndlMouseDragIndiv(mouseX, mouseY,pmouseX, pmouseY,mouseClickIn3D,mseDragInWorld,mseBtn);        //handle specific, non-trajectory functionality for implementation of window
            }
        }
        return mod;
    }//handleMouseDrag
    
    public void handleMouseRelease(){
        if(!getFlags(showIDX)){return;}
        if(getFlags(uiObjMod)){
            for(int i=0;i<guiObjs_Numeric.length;++i){if(guiObjs_Numeric[i].getFlags(myGUIObj.usedByWinsIDX)){setUIWinVals(i);}}
            setFlags(uiObjMod, false);
            msClkObj = -1;    
        }//some object was clicked - pass the values out to all windows
        if (getFlags(editingTraj)){    this.tmpDrawnTraj.endEditObj();}    //this process assigns tmpDrawnTraj to owning window's traj array
        if (getFlags(drawingTraj)){    this.tmpDrawnTraj.endDrawObj(getMsePoint(pa.Mouse()));}    //drawing curve
        msClkObj = -1;    
        hndlMouseRelIndiv();//specific instancing window implementation stuff
        this.tmpDrawnTraj = null;
    }//handleMouseRelease    
    
    //release shift/control/alt keys
    public void endShiftKey(){
        if(!getFlags(showIDX)){return;}
        //
        endShiftKey_Indiv();
    }
    public void endAltKey(){
        if(!getFlags(showIDX)){return;}
        if(getFlags(drawingTraj)){this.tmpDrawnTraj.endDrawObj(getMsePoint(pa.Mouse()));}    
        endAltKey_Indiv();
        this.tmpDrawnTraj = null;
    }    
    public void endCntlKey(){
        if(!getFlags(showIDX)){return;}
        //
        endCntlKey_Indiv();
    }    
            
    //drawn trajectory stuff    
    public void startBuildDrawObj(){
        pa.flags[pa.drawing] = true;
        //drawnTrajAra[curDrnTrajScrIDX][curDrnTrajStaffIDX].startBuildTraj();
        tmpDrawnTraj= new myDrawnSmplTraj(pa,this,topOffY,trajFillClrCnst, trajStrkClrCnst, getFlags(trajPointsAreFlat), !getFlags(trajPointsAreFlat));
        tmpDrawnTraj.startBuildTraj();
        setFlags(drawingTraj, true);
    }
    
    //finds closest point to p in sPts - put dist in d
    public final int findClosestPt(myPoint p, double[] d, myPoint[] _pts){
        int res = -1;
        double mindist = 99999999, _d;
        for(int i=0; i<_pts.length; ++i){_d = myPoint._dist(p,_pts[i]);if(_d < mindist){mindist = _d; d[0]=_d;res = i;}}    
        return res;
    }

    //initialize circle so that it will draw at location of edit
    public void setEditCueCircle(int idx,myPoint mse){
        setFlags(showTrajEditCrc, true);
        editCrcCtrs[idx].set(mse.x, mse.y, 0);
        editCrcCurRads[idx] = editCrcRads[idx];
    }
    
    public void rebuildAllDrawnTrajs(){
        for(TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTreeMap : drwnTrajMap.values()){
            if((tmpTreeMap != null) && (tmpTreeMap.size() != 0)) {
                for(int i =0; i<tmpTreeMap.size(); ++i){
                    ArrayList<myDrawnSmplTraj> tmpAra = tmpTreeMap.get(getTrajAraKeyStr(i));            
                    if(null!=tmpAra){    for(int j =0; j<tmpAra.size();++j){    tmpAra.get(j).rebuildDrawnTraj();}}
                }
            }    
        }            
    }//rebuildAllDrawnTrajs
    
    //debug data to display on screen
    //get string array for onscreen display of debug info for each object
    public String[] getDebugData(){
        ArrayList<String> res = new ArrayList<String>();
        List<String>tmp;
        for(int j = 0; j<guiObjs_Numeric.length; j++){tmp = Arrays.asList(guiObjs_Numeric[j].getStrData());res.addAll(tmp);}
        return res.toArray(new String[0]);    
    }
    
    //set colors of the trajectory for this window
    public void setTrajColors(int _tfc, int _tsc){trajFillClrCnst = _tfc;trajStrkClrCnst = _tsc;initTmpTrajStuff(getFlags(trajPointsAreFlat));}
    //get key used to access arrays in traj array
    protected String getTrajAraKeyStr(int i){return trajNameAra[i];}
    protected int getTrajAraIDXVal(String str){for(int i=0; i<trajNameAra.length;++i){if(trajNameAra[i].equals(str)){return i;}}return -1; }

    //add trajectory to appropriately keyed current trajectory ara in treemap    
    protected void processTrajectory(myDrawnSmplTraj drawnNoteTraj){
        TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTreeMap = drwnTrajMap.get(this.curDrnTrajScrIDX);
        ArrayList<myDrawnSmplTraj> tmpAra;
        if(curTrajAraIDX != -1){        //make sure some trajectory/staff has been selected
            if((tmpTreeMap != null) && (tmpTreeMap.size() != 0) ) {
                tmpAra = tmpTreeMap.get(getTrajAraKeyStr(curTrajAraIDX));            
                //if((null==tmpAra) || (pa.flags[pa.clearTrajWithNew])){    
                //for this application always wish to clear traj
                    tmpAra = new ArrayList<myDrawnSmplTraj>();
                //}
                //lastTrajIDX = tmpAra.size();
                tmpAra.add(drawnNoteTraj);                 
            } else {//empty or null tmpTreeMap - tmpAra doesn't exist
                tmpAra = new ArrayList<myDrawnSmplTraj>();
                tmpAra.add(drawnNoteTraj);
                //lastTrajIDX = tmpAra.size();
                if(tmpTreeMap == null) {tmpTreeMap = new TreeMap<String,ArrayList<myDrawnSmplTraj>>();} 
            }
            tmpTreeMap.put(getTrajAraKeyStr(curTrajAraIDX), tmpAra);
            processTrajIndiv(drawnNoteTraj);
        }    
        //individual traj processing
    }
    
    public void clearAllTrajectories(){//int instrIdx){
        TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTreeMap = drwnTrajMap.get(this.curDrnTrajScrIDX);
        ArrayList<myDrawnSmplTraj> tmpAra;
        if(curTrajAraIDX != -1){        //make sure some trajectory/staff has been selected
            if((tmpTreeMap != null) && (tmpTreeMap.size() != 0) ) {
                tmpTreeMap.put(getTrajAraKeyStr(curTrajAraIDX), new ArrayList<myDrawnSmplTraj>());
            }
        }    
    }//clearAllTrajectories
    
    //add another screen to this window - need to handle specific trajectories - always remake traj structure
    public void addSubScreenToWin(int newWinKey){
        modTrajStructs(newWinKey, "",false);
        
        addSScrToWinIndiv(newWinKey);
    }
    public void addTrajToSubScreen(int subScrKey, String newTrajKey){
        modTrajStructs(subScrKey, newTrajKey,false);        
        addTrajToScrIndiv(subScrKey, newTrajKey);
    }
    
    public void delSubScreenToWin(int delWinKey){
        modTrajStructs(delWinKey, "",true);        
        delSScrToWinIndiv(delWinKey);
    }
    public void delTrajToSubScreen(int subScrKey, String newTrajKey){
        modTrajStructs(subScrKey, newTrajKey,true);
        delTrajToScrIndiv(subScrKey,newTrajKey);
    }
        
    //updates values in UI with programatic changes 
    protected boolean setWinToUIVals(int UIidx, double val){return val == guiObjs_Numeric[UIidx].setVal(val);}

    //UI controlled debug functionality
    public abstract void clickDebug(int btnNum);
    protected void clearDBGBtnState(int btnNum, boolean isSlow){pa.clearDBGBtnSt(btnNum,isSlow);}
    //UI controlled auxiliary functionality
    public abstract void clickFunction(int btnNum);
    protected void clearFuncBtnState(int btnNum, boolean isSlow) {pa.clearFuncBtnSt(btnNum,isSlow);}    
    
    protected abstract void initDrwnTrajIndiv();
    protected abstract void addSScrToWinIndiv(int newWinKey);
    protected abstract void addTrajToScrIndiv(int subScrKey, String newTrajKey);
    protected abstract void delSScrToWinIndiv(int idx);
    protected abstract void delTrajToScrIndiv(int subScrKey, String newTrajKey);
    
    protected abstract myPoint getMsePtAs3DPt(int mouseX, int mouseY);
    
    //implementing class' necessary functions - implement for each individual window
    protected abstract boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld);
    protected abstract boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn);
    protected abstract boolean hndlMouseDragIndiv(int mouseX, int mouseY,int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld, int mseBtn);
    protected abstract void snapMouseLocs(int oldMouseX, int oldMouseY, int[] newMouseLoc);    
    
    protected abstract void hndlMouseRelIndiv();
    
    protected abstract void endShiftKey_Indiv();
    protected abstract void endAltKey_Indiv();
    protected abstract void endCntlKey_Indiv();
    
    //ui init routines
    protected abstract void setupGUIObjsAras();    
    protected abstract void setUIWinVals(int UIidx);        //set prog values from ui
    protected abstract String getUIListValStr(int UIidx, int validx);
    protected abstract void processTrajIndiv(myDrawnSmplTraj drawnTraj);
    
    //file io used from selectOutput/selectInput - 
    //take loaded params and process
    protected abstract void hndlFileLoadIndiv(String[] vals, int[] stIdx);
    //accumulate array of params to save
    protected abstract List<String> hndlFileSaveIndiv();    
    
    protected abstract void initMe();
    protected abstract void resizeMe(float scale);    
    protected abstract void showMe();
    protected abstract void closeMe();    
    protected abstract boolean simMe(float modAmtSec);
    protected abstract void stopMe();
    protected abstract void setCameraIndiv(float[] camVals);
    protected abstract void drawMe(float animTimeMod);    
    protected abstract void drawRightSideInfoBar(float modAmtMillis);
    protected abstract void drawOnScreenStuffPriv(float modAmtMillis);
    
    public String toString(){
        String res = "Window : "+name+" ID: "+ID+" Fill :("+fillClr[0]+","+fillClr[1]+","+fillClr[2]+","+fillClr[3]+
                ") | Stroke :("+fillClr[0]+","+fillClr[1]+","+fillClr[2]+","+fillClr[3]+") | Rect : ("+
                String.format("%.2f",rectDim[0])+","+String.format("%.2f",rectDim[1])+","+String.format("%.2f",rectDim[2])+","+String.format("%.2f",rectDim[3])+")\n";    
        return res;
    }
}//dispWindow


//displays sidebar menu of interaction and functionality
class mySideBarMenu extends Base_DispWindow{
    //booleans in main program - need to have labels in idx order, even if not displayed
    public final String[] truePFlagNames = {//needs to be in order of flags
            "Debug Mode",
            "Final init Done",
            "Save Anim",         
            "Shift-Key Pressed",
            "Alt-Key Pressed",
            "Cntl-Key Pressed",
            "Click interact",     
            "Drawing Curve",
            "Changing View",    
            "Stop Simulation",
            "Single Step",
            "Displaying UI Menu",
            "Reverse Drawn Trajectory"
            };
    
    public final String[] falsePFlagNames = {//needs to be in order of flags
            "Debug Mode",    
            "Final init Done",
            "Save Anim",         
            "Shift-Key Pressed",
            "Alt-Key Pressed",
            "Cntl-Key Pressed",
            "Click interact",     
            "Drawing Curve",
            "Changing View",         
            "Run Simulation",
            "Single Step",
            "Displaying UI Menu",
            "Reverse Drawn Trajectory"
            };
    
    
    public int[][] pFlagColors;
    
    public final int clkFlgsStY = 10;
    
    public final String[] StateBoolNames = {"Shift","Alt","Cntl","Click", "Draw","View"};
    //multiplier for displacement to display text label for stateboolnames
    public final float[] StrWdMult = new float[]{-3.0f,-3.0f,-3.0f,-3.2f,-3.5f,-2.5f};
    public int[][] stBoolFlagColors;

    //    //GUI Objects    
    //idx's of objects in gui objs array    
    public static final int 
        gIDX_TimeStep             = 0;//, 
    public final int numGUIObjs = 0;                            //# of gui objects for ui
    
    //private child-class flags - window specific
    public static final int 
            mseClickedInBtnsIDX         = 0;                    //the mouse was clicked in the button region of the menu and a click event was processed
    //private flag based buttons - ui menu won't have these
    public static final int numPrivFlags = 1;
    
    //GUI Buttons
    public float minBtnClkY;            //where buttons should start on side menu

    public static final String[] guiBtnRowNames = new String[]{ 
            "Window","Win-specific Functions","DEBUG","File"};

    public static final int 
            btnShowWinIdx = 0,                 //which window to show
            btnAuxFuncIdx = 1,            //aux functionality
            btnDBGSelCmpIdx = 2,            //debug
            btnFileCmdIdx = 3;                //load/save files
    //names for each row of buttons - idx 1 is name of row
    public final String[][] guiBtnNames = new String[][]{
        new String[]{pa.winTitles[1], pa.winTitles[2]},                            //display specific windows - multi-select/ always on if sel
        new String[]{"Func 1","Func 2","Func 3","Func 4","Func 5"},            //per-window user functions - momentary
        new String[]{"Dbg 1","Dbg 2","Dbg 3","Dbg 4"},                        //DEBUG - momentary
        new String[]{"Load Txt File","Save Txt File"}                            //load an existing score, save an existing score - momentary        
    };
    //default names, to return to if not specified by user
    public final String[][] defaultUIBtnNames = new String[][]{
        new String[]{"John's Window", "Alt Window"},                            //display specific windows - multi-select/ always on if sel
        new String[]{"Func 1","Func 2","Func 3","Func 4","Func 5"},            //per-window user functions - momentary
        new String[]{"Dbg 1","Dbg 2","Dbg 3","Dbg 4"},                        //DEBUG - momentary
        new String[]{"Load Txt File","Save Txt File"}                            //load an existing score, save an existing score - momentary        
    };
    //whether buttons are momentary or not (on only while being clicked)
    public boolean[][] guiBtnInst = new boolean[][]{
        new boolean[]{false,false},                                 //display specific windows - multi-select/ always on if sel
        new boolean[]{false,false,false,false,false},                   //functionality - momentary
        new boolean[]{false,false,false,false},                           //debug - momentary
        new boolean[]{true,true},                                      //load an existing score, save an existing score - momentary    
    };        
    //whether buttons are waiting for processing to complete (for non-momentary buttons)
    public boolean[][] guiBtnWaitForProc = new boolean[][]{
        new boolean[]{false,false},                                 //display specific windows - multi-select/ always on if sel
        new boolean[]{false,false,false,false,false},                   //functionality - momentary
        new boolean[]{false,false,false,false},                           //debug - momentary
        new boolean[]{false,false},                                      //load an existing score, save an existing score - momentary    
    };        
    
    
    //whether buttons are disabled(-1), enabled but not clicked/on (0), or enabled and on/clicked(1)
    public int[][] guiBtnSt = new int[][]{
        new int[]{0,0},                                        //display specific windows - multi-select/ always on if sel
        new int[]{0,0,0,0,0},                                       //debug - momentary
        new int[]{0,0,0,0},                                       //debug - momentary
        new int[]{0,0}                                              //load an existing score, save an existing score - momentary    
    };
    
    public int[] guiBtnStFillClr;
    public int[] guiBtnStTxtClr;
    //row and column of currently clicked-on button (for display highlight as pressing)
    public int[] curBtnClick = new int[]{-1,-1};

    public mySideBarMenu(DancingBalls _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed, String _winTxt, boolean _canDrawTraj) {
        super(_p, _n, _flagIdx, fc, sc,  rd, rdClosed, _winTxt, _canDrawTraj);
        guiBtnStFillClr = new int[]{        //button colors based on state
                DancingBalls.gui_White,                                //disabled color for buttons
                DancingBalls.gui_LightGray,                                //not clicked button color
                DancingBalls.gui_LightBlue,                                    //clicked button color
            };
        guiBtnStTxtClr = new int[]{            //text color for buttons
                DancingBalls.gui_LightGray,                                    //disabled color for buttons
                DancingBalls.gui_Black,                                    //not clicked button color
                DancingBalls.gui_Black,                                    //clicked button color
            };            
        super.initThisWin(_canDrawTraj, false, true);
    }
    
    //set up initial colors for papplet's flags for display
    public void initPFlagColors(){
        pFlagColors = new int[pa.numFlags][3];
        for (int i = 0; i < pa.numFlags; ++i) { pFlagColors[i] = new int[]{(int) pa.random(150),(int) pa.random(100),(int) pa.random(150)}; }        
        stBoolFlagColors = new int[pa.numStFlagsToShow][3];
        stBoolFlagColors[0] = new int[]{255,0,0};
        stBoolFlagColors[1] = new int[]{0,255,0};
        stBoolFlagColors[2] = new int[]{0,0,255};        
        for (int i = 3; i < pa.numStFlagsToShow; ++i) { stBoolFlagColors[i] = new int[]{100+((int) pa.random(150)),150+((int) pa.random(100)),150+((int) pa.random(150))};        }
    }
    @Override
    protected void snapMouseLocs(int oldMouseX, int oldMouseY, int[] newMouseLoc){}//not a snap-to window
        
    //call this from each new window to set function names, if specified, when window gets focus
    public void setBtnNames(int rowIdx, String[] btnNames) {
        String[] replAra = ((null==btnNames) || (btnNames.length != guiBtnNames[rowIdx].length)) ? defaultUIBtnNames[rowIdx] : btnNames;
        for(int i=0;i<guiBtnNames[rowIdx].length;++i) {guiBtnNames[rowIdx][i]=replAra[i];}
    }//setFunctionButtonNames
    
    
    @Override
    //initialize all private-flag based UI buttons here - called by base class
    public void initAllUIButtons(){
        truePrivFlagNames = new String[]{                                //needs to be in order of flags
        };
        falsePrivFlagNames = new String[]{            //needs to be in order of flags
        };
        privModFlgIdxs = new int[]{};
        numClickBools = privModFlgIdxs.length;    
    }//
    
    @Override
    protected void initMe() {//init/reinit this window
        setFlags(closeable, false);
//        setFlags(uiObjsAreVert, true);
        initPrivFlags(numPrivFlags);        
    }    
    //set flag values and execute special functionality for this sequencer
    @Override
    public void setPrivFlags(int idx, boolean val){
        int flIDX = idx/32, mask = 1<<(idx%32);
        privFlags[flIDX] = (val ?  privFlags[flIDX] | mask : privFlags[flIDX] & ~mask);
        switch (idx) {//special actions for each flag
            case mseClickedInBtnsIDX : {
                
            }
        }
    }

    //initialize structure to hold modifiable menu regions
    @Override
    protected void setupGUIObjsAras(){                        //called from super.initThisWin
        guiMinMaxModVals = new double [][]{    {}};//min max mod values        
        guiStVals = new double[]{};
        guiObjNames = new String[]{};        
        
        //idx 0 is treat as int, idx 1 is obj has list vals, idx 2 is object gets sent to windows
        guiBoolVals = new boolean [][]{{}};
        
        minBtnClkY = (pa.numFlagsToShow+3) * yOff + clkFlgsStY;                                        //start of buttons from under boolean flags    
        setUIClkCoords(rectDim[0] + .1f * rectDim[2],minBtnClkY + (guiBtnRowNames.length * 2.0f) * yOff,rectDim[0] + .99f * rectDim[2],0);//last val over-written by actual value in buildGuiObjs
        guiObjs_Numeric = new myGUIObj[numGUIObjs];            //list of modifiable gui objects
        if(0!=numGUIObjs){
            buildGUIObjs(guiObjNames,guiStVals,guiMinMaxModVals,guiBoolVals, new double[]{xOff,yOff});
        } else {
            uiClkCoords[3] = uiClkCoords[1];        //set y start values
        }
    }
    
    //check if buttons clicked
    private boolean checkButtons(int mseX, int mseY){
        double stY = minBtnClkY + rowStYOff, endY = stY+yOff, stX = 0, endX, widthX; //btnLblYOff            
        for(int row=0; row<guiBtnRowNames.length;++row){
            widthX = rectDim[2]/(1.0f * guiBtnNames[row].length);
            stX =0;    endX = widthX;
            for(int col =0; col<guiBtnNames[row].length;++col){    
                if((pa.ptInRange(mseX, mseY,stX, stY, endX, endY)) && (guiBtnSt[row][col] != -1)){
                    handleButtonClick(row,col);
                    return true;
                }                    
                stX += widthX;    endX += widthX; 
            }
            stY = endY + yOff+ rowStYOff;endY = stY + yOff;                
        }
        return false;
    }//handleButtonClick    
    //public void clearAllBtnStates(){for(int row=0; row<guiBtnRowNames.length;++row){for(int col =0; col<guiBtnNames[row].length;++col){if((guiBtnInst[row][col]) && (guiBtnSt[row][col] ==1)){    guiBtnSt[row][col] = 0;}}}}
    
    //turn off buttons that may be on and should be turned off - called at release of mouse - check for mouse loc before calling (in button region)?
    public void clearAllBtnStates(){
        if(this.getPrivFlags(mseClickedInBtnsIDX)) {
            //guiBtnWaitForProc should only be set for non-momentary buttons when they are pushed and cleared when whatever they are do is complete
            for(int row=0; row<guiBtnRowNames.length;++row){for(int col =0; col<guiBtnNames[row].length;++col){                
                if((guiBtnSt[row][col]==1) && (guiBtnInst[row][col]  || !guiBtnWaitForProc[row][col])){    guiBtnSt[row][col] = 0;}//btn is on, and either is momentary or it is not waiting for processing
            }}
            this.setPrivFlags(mseClickedInBtnsIDX, false);
        }
    }//clearAllBtnStates
    
    //set non-momentary buttons to be waiting for processing complete comand
    public void setWaitForProc(int row, int col) {
        if(!guiBtnInst[row][col]) {    guiBtnWaitForProc[row][col] = true;}        
    }
    //handle click on button region of menubar
    public void handleButtonClick(int row, int col){
        int val = guiBtnSt[row][col];//initial state, before being changed
        guiBtnSt[row][col] = (guiBtnSt[row][col] + 1)%2;//change state
        //if not momentary buttons, set wait for proc to true
        setWaitForProc(row,col);
        switch(row){
            case btnShowWinIdx         : {pa.handleShowWin(col, val);break;}
            case btnAuxFuncIdx         : {pa.handleFuncSelCmp(col, val);break;}
            case btnDBGSelCmpIdx      : {pa.handleDBGSelCmp(col, val);break;}
            case btnFileCmdIdx         : {pa.handleFileCmd(col, val);break;}
        }                
    }    

    //handle the display of UI objects backed by a list
    @Override
    protected String getUIListValStr(int UIidx, int validx){
        switch(UIidx){
//        case gIDX_UIElem2List         : {return keySigs[(validx % keySigs.length)]; }
//        case gIDX_UIElem3List    : {return ""+ noteVals[(validx % noteVals.length)]+ " ("+timeSigDenom[(validx % timeSigDenom.length)]+")";}        
        }
        return "";
    }//dispUIListObj
    //uses passed time
    @Override //only send new values if actually new values
    protected void setUIWinVals(int UIidx){
        switch(UIidx){
//        //set lcl/global vals
//        case gIDX_UIElem2List         : {
////            int sel = (int)guiObjs_Numeric[UIidx].getVal() % keySigs.length;
////            if (sel != Base_DispWindow.glblKeySig.keyIdx){for(int i=1; i<pa.dispWinFrames.length; ++i){pa.dispWinFrames[i].setGlobalKeySigVal(sel);} pa.setFlags(pa.forceInKey,false); }            
//            break;}
//        case gIDX_UIElem3     : 
//        case gIDX_UIElem3List     : {
////            int tsDenom = timeSigDenom[(int)guiObjs_Numeric[gIDX_UIElem3List].getVal() %timeSigDenom.length],
////                    tsNum = (int)guiObjs_Numeric[gIDX_TimeSigNum].getVal();
////            durType dType = pa.getDurTypeForNote(tsDenom);            
////            if((dType != glblBeatNote) || (glblTimeSig.beatPerMeas != tsNum) || (glblTimeSig.beatNote != tsDenom)){            
////                for(int i=1; i<pa.dispWinFrames.length; ++i){pa.dispWinFrames[i].setGlobalTimeSigVal(tsNum,tsDenom, dType);} 
////            }
//            break;}
//        case gIDX_UIElem1            : {
//            float tmpTempo = (float)guiObjs_Numeric[UIidx].getVal();
////            if(PApplet.abs(tmpTempo - glblTempo) > pa.feps){for(int i=1; i<pa.dispWinFrames.length; ++i){pa.dispWinFrames[i].setGlobalTempoVal(tmpTempo);}}
//            break;}
        }            
    }//setUIWinVals
    @Override
    protected boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld){        return false;    }
    @Override
    protected boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn) {    
        if((!pa.ptInRange(mouseX, mouseY, rectDim[0], rectDim[1], rectDim[0]+rectDim[2], rectDim[1]+rectDim[3]))){return false;}//not in this window's bounds, quit asap for speedz
        int i = (int)((mouseY-(yOff + yOff + clkFlgsStY))/(yOff));                    //TODO Awful - needs to be recalced, dependent on menu being on left
        if((i>=0) && (i<pa.numFlagsToShow)){
            pa.setFlags(pa.flagsToShow.get(i),!pa.flags[pa.flagsToShow.get(i)]);return true;    }
        else if(pa.ptInRange(mouseX, mouseY, 0, minBtnClkY, uiClkCoords[2], uiClkCoords[1])){
            boolean clkInBtnRegion = checkButtons(mouseX, mouseY);
            if(clkInBtnRegion) { this.setPrivFlags(mseClickedInBtnsIDX, true);}
            return clkInBtnRegion;
        }//in region where clickable buttons are - uiClkCoords[1] is bottom of buttons
        return false;
    }
    @Override
    protected boolean hndlMouseDragIndiv(int mouseX, int mouseY,int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld, int mseBtn) {//regular UI obj handling handled elsewhere - custom UI handling necessary to call main window        
        boolean res = pa.dispWinFrames[pa.curFocusWin].hndlMouseDragIndiv(mouseX, mouseY,pmouseX, pmouseY, mouseClickIn3D, mseDragInWorld, mseBtn);
        return res;    }
    @Override
    protected void hndlMouseRelIndiv() {    clearAllBtnStates();}

    private void drawSideBarBooleans(){
        //draw booleans and their state
        pa.translate(10,yOff*2);
        pa.setColorValFill(DancingBalls.gui_Black);
        pa.text("Boolean Flags",0,yOff*.20f);
        pa.translate(0,clkFlgsStY);
        for(int idx =0; idx<pa.numFlagsToShow; ++idx){
            int i = pa.flagsToShow.get(idx);
            if(pa.flags[i] ){                                                    dispMenuTxtLat(truePFlagNames[i],pFlagColors[i], true);            }
            else {    if(truePFlagNames[i].equals(falsePFlagNames[i])) {        dispMenuTxtLat(truePFlagNames[i],new int[]{180,180,180}, false);}    
                    else {                                                    dispMenuTxtLat(falsePFlagNames[i],new int[]{0,255-pFlagColors[i][1],255-pFlagColors[i][2]}, true);}        
            }
        }    
    }//drawSideBarBooleans
    private void drawSideBarStateBools(){ //numStFlagsToShow
        pa.translate(110,10);
        float xTrans = (int)((pa.menuWidth-100) / pa.numStFlagsToShow);
        for(int idx =0; idx<pa.numStFlagsToShow; ++idx){
            dispBoolStFlag(StateBoolNames[idx],stBoolFlagColors[idx], pa.flags[pa.stateFlagsToShow.get(idx)],StrWdMult[idx]);            
            pa.translate(xTrans,0);
        }
    }
    
    //draw UI buttons
    private void drawSideBarButtons(){
        pa.translate(xOff*.5f,(float)minBtnClkY);
        pa.setFill(new int[]{0,0,0}, 255);
        for(int row=0; row<guiBtnRowNames.length;++row){
            pa.text(guiBtnRowNames[row],0,-yOff*.15f);
            pa.translate(0,rowStYOff);
            float xWidthOffset = rectDim[2]/(1.0f * guiBtnNames[row].length), halfWay;
            pa.pushMatrix();pa.pushStyle();
            pa.strokeWeight(1.0f);
            pa.stroke(0,0,0,255);
            pa.noFill();
            pa.translate(-xOff*.5f, 0);
            for(int col =0; col<guiBtnNames[row].length;++col){
                halfWay = (xWidthOffset - pa.textWidth(guiBtnNames[row][col]))/2.0f;
                pa.setColorValFill(guiBtnStFillClr[guiBtnSt[row][col]+1]);
                pa.rect(0,0,xWidthOffset, yOff);    
                pa.setColorValFill(guiBtnStTxtClr[guiBtnSt[row][col]+1]);
                pa.text(guiBtnNames[row][col], halfWay, yOff*.75f);
                pa.translate(xWidthOffset, 0);
            }
            pa.popStyle();    pa.popMatrix();                        
            pa.translate(0,btnLblYOff);
        }
    }//drawSideBarButtons    
    @Override//for windows to draw on screen
    protected void drawRightSideInfoBar(float modAmtMillis) {}
    @Override
    protected void drawMe(float animTimeMod) {
        pa.pushMatrix();pa.pushStyle();
            drawSideBarBooleans();                //toggleable booleans 
        pa.popStyle();    pa.popMatrix();    
        pa.pushMatrix();pa.pushStyle();
            drawSideBarStateBools();                //lights that reflect various states
        pa.popStyle();    pa.popMatrix();    
        pa.pushMatrix();pa.pushStyle();            
            drawSideBarButtons();                        //draw buttons
        pa.popStyle();    pa.popMatrix();    
        pa.pushMatrix();pa.pushStyle();
            drawGUIObjs();                    //draw what global user-modifiable fields are currently available
        pa.popStyle();    pa.popMatrix();            
        pa.pushMatrix();pa.pushStyle();
            drawWindowGuiObjs();        
        pa.popStyle();    pa.popMatrix();    
    }
    @Override
    public void drawCustMenuObjs(){}
    
    private void drawWindowGuiObjs(){
        if(pa.curFocusWin != -1){
            pa.pushMatrix();pa.pushStyle();
            pa.dispWinFrames[pa.curFocusWin].drawGUIObjs();                    //draw what user-modifiable fields are currently available
            pa.dispWinFrames[pa.curFocusWin].drawClickableBooleans();                    //draw what user-modifiable fields are currently available
            pa.dispWinFrames[pa.curFocusWin].drawCustMenuObjs();                    //customizable menu objects for each window
            pa.popStyle();    pa.popMatrix();    
        }
    }
    
    @Override
    protected void drawOnScreenStuffPriv(float modAmtMillis){}
    //no custom camera handling for menu
    @Override
    protected void setCameraIndiv(float[] camVals){}
    @Override
    public void hndlFileLoadIndiv(String[] vals, int[] stIdx) {
        
    }
    @Override
    public List<String> hndlFileSaveIndiv() {
        List<String> res = new ArrayList<String>();

        return res;
    }
    @Override
    public void drawClickableBooleans() {    }//this is only for non-sidebar menu windows, to display their own personal buttons
    @Override
    public void clickFunction(int btnNum) {}        //only for display windows
    @Override
    public void clickDebug(int btnNum){}    

    @Override
    protected void initTrajStructs() {}
    @Override
    protected void endShiftKey_Indiv() {}
    @Override
    protected void endAltKey_Indiv() {}
    @Override
    protected void endCntlKey_Indiv() {}
    @Override
    protected void closeMe() {}
    @Override
    protected void showMe() {}
    @Override
    protected void resizeMe(float scale) {}    
    @Override
    protected boolean simMe(float modAmtSec) {return true;}
    @Override
    protected void stopMe() {}
    @Override
    protected void addSScrToWinIndiv(int newWinKey){}
    @Override
    protected void addTrajToScrIndiv(int subScrKey, String newTrajKey){}
    @Override
    protected void delSScrToWinIndiv(int idx) {}    
    @Override
    protected void delTrajToScrIndiv(int subScrKey, String newTrajKey) {}        
    //no trajectory here
    @Override
    protected void processTrajIndiv(myDrawnSmplTraj drawnTraj){}    
    @Override
    protected void initDrwnTrajIndiv(){}
    @Override
    public String toString(){
        String res = super.toString();
        return res;
    }

    @Override
    protected myPoint getMsePtAs3DPt(int mouseX, int mouseY) {
        // TODO Auto-generated method stub
        return null;
    }

}//mySideBarMenu



class myScrollBars{
    public DancingBalls pa;
    public Base_DispWindow win;
    public static int scrBarCnt = 0;
    public int ID;
    //displacement for scrolling display - x/y location of window, x/y zoom - use scrollbars&zoomVals - displacement is translate, zoom is scale
    public float[] scrollZoomDisp,
    //start x,y, width, height of scroll bar region 
         hScrlDims,    
    //start x,y, width, height of scroll bar region 
         vScrlDims;
    private final float thk = 20, thmult = 1.5f;                //scrollbar dim is 25 pxls wide    
    
    public float[][] arrowDims = new float[][]{{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}};
    //hthumb x,y width,height,vthumb x,y width,height
    public float[][] thmbs = new float[][]{{0,0},{0,0}};
    //hthumb min/max, vthumb min/max bounds
    public float[][] thmbBnds = new float[][]{{0,0},{0,0}};
    
    public static final int
        upIDX = 0,
        dnIDX = 1,
        ltIDX = 2,
        rtIDX = 3,
        hThmbIDX = 0,
        vThmbIDX = 1;
    
    public int[][] clrs;            //colors for thumb and up/down/left/right arrows
    
    public myScrollBars(DancingBalls _pa,Base_DispWindow _win){
        pa = _pa;
        win = _win;
        ID = scrBarCnt++;
        setSize();
        clrs = new int[][]{ pa.getRndClr(),pa.getRndClr(),pa.getRndClr(),pa.getRndClr(),pa.getRndClr(),pa.getRndClr()};
    }//myScrollBars
    
    public void setSize(){
        float rectWidth = win.rectDim[0]+win.rectDim[2],vScrlStartY = win.rectDim[1]+(win.closeBox[3]),
                rectHeight = win.rectDim[3]-(vScrlStartY);
        vScrlDims = new float[]{rectWidth - thk,vScrlStartY, thk,  rectHeight-thk};
        hScrlDims = new float[]{win.rectDim[0], win.rectDim[1]+win.rectDim[3] - thk,win.rectDim[2]-thk,thk};

        thmbs[hThmbIDX] = new float[]{hScrlDims[0]+thk,hScrlDims[1],thmult*thk,thk};            //location/dims of thumb
        thmbBnds[hThmbIDX] = new float[]{thmbs[hThmbIDX][0],hScrlDims[2]-thmbs[hThmbIDX][2]-thk};        //min/max x val of horiz thumb
        
        thmbs[vThmbIDX] = new float[]{vScrlDims[0],(vScrlDims[1]+thk),thk,thmult*thk};            //location/dims of thumb
        thmbBnds[vThmbIDX] = new float[]{thmbs[vThmbIDX][1],vScrlDims[3]-thmbs[vThmbIDX][3]-thk};        //min/max of y val of vert thumb
        
        //arrow boxes - x,y,widht,height
        arrowDims = new float[][]{
            {vScrlDims[0],vScrlDims[1],thk,thk},            //up
            {vScrlDims[0],vScrlDims[1]+vScrlDims[3]-thk,thk,thk},        //down
            {hScrlDims[0],                    hScrlDims[1],thk,thk},            //left
            {hScrlDims[0]+hScrlDims[2]-thk,hScrlDims[1],thk,thk}};        //right

    }
    public void drawMe(){
        pa.pushMatrix(); pa.pushStyle();
        pa.setColorValFill(DancingBalls.gui_LightGray);
        pa.setColorValStroke(DancingBalls.gui_Black);
        pa.strokeWeight(1.0f);
        pa.rect(vScrlDims);
        pa.rect(hScrlDims);
        for(int i =0; i<arrowDims.length;++i){
            pa.setFill(clrs[i]);
            pa.rect(arrowDims[i]);
        }
        pa.popStyle();pa.popMatrix();
        pa.pushMatrix(); pa.pushStyle();
        for(int i =0; i<thmbs.length;++i){
            pa.setFill(clrs[i + 4]);
            pa.rect(thmbs[i]);
        }
        
        pa.popStyle();pa.popMatrix();
    }//drawMe
    
    
    public boolean msePtInRect(int x, int y, float[] r){return ((x > r[0])&&(x <= r[0]+r[2])&&(y > r[1])&&(y <= r[1]+r[3]));}        
    public boolean handleMouseClick(int mouseX, int mouseY, myPoint mouseClickIn3D){
        
        return false;    
    }//handleMouseClick
    public boolean handleMouseDrag(int mouseX, int mouseY,int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld){    
        
        return false;
    }//handleMouseDrag
    public void handleMouseRelease(){
        
        
    }//handleMouseRelease
    
    
    
}//myScrollBars

