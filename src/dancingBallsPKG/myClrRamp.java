package dancingBallsPKG;

import java.util.ArrayList;

import processing.core.*; 

public class myClrRamp {
    public DancingBalls pa;    
    
    public int R0=250, G0=245, B0=20, R1=17, G1=60, B1=242, k=30;
    
    public float sIncr = .005f;
    int numGrads = 7;                    //rgb, xyz, lab, lch, userDef1 userDef2
    
    final  int USR3 = 6;
    //public String[] gradNames = new String[]{"RGB","XYZ","LAB","LCH","Modified XYZ","Modified LAB","Modified LCH"};
    public myClrGrad grads;            //gradients
    
    
    public float barThick = 3;
    public ArrayList<myClrPoint> usrPts;        //user controlled color points
    //public ArrayList[] clrPtsArrays = new ArrayList[numGrads];
    public ArrayList<myClrPoint> clrPtsArrays = new ArrayList<myClrPoint>();// only using idx 6 = new ArrayList[numGrads];
    
    public myClrPoint[] midPoints;        //midpoints of line segments of box - use to find knots for spline
    
    
    public float userPtRad = 7, clrPtRad = 2;    //user controlled color point radius and derived color point radius
    //public myPoint ptA, ptB, ptC;            //user controlled colors
    public String mode = "rgb";
    public float[] scaleVals;
    int numClrPts = 3;                    //# of points in color box
    public Layer layer;

    // FROM: http://rsbweb.nih.gov/ij/plugins/download/Color_Space_Converter.java
    public double[] D65 = {95.0429f, 100.0f, 108.8900f};
    public double[] whitePoint = D65;    
    public double[][] Mi  = {{ 3.2406f, -1.5372f, -0.4986f},
                             {-0.9689f,  1.8758f,  0.0415f},
                             { 0.0557f, -0.2040f,  1.0570f}};
    public double[][] M   = {{0.4124f, 0.3576f,  0.1805f},
                             {0.2126f, 0.7152f,  0.0722f},
                             {0.0193f, 0.1192f,  0.9505f}};
    
    public myClrRamp(DancingBalls _p){
        pa = _p;      
        layer = new Layer(50, 10);
        scaleVals = new float[]{pa.gridDimX/255.0f,pa.gridDimY/255.0f,pa.gridDimZ/255.0f};
        
        float y = pa.height *.15f;
        grads = new myClrGrad(pa, this, pa.width *.85f, y, "Modified LCH");//(CAProject3 _pa, float _x, float _y){            

        usrPts = new ArrayList<myClrPoint>();        
        myPoint ptA =new myPoint(R0,G0,B0),
        ptB= new myPoint(R1,G1,B1);
        usrPts.add(new myClrPoint(pa, "Point A", ptA.x, ptA.y, ptA.z, userPtRad));
        usrPts.add(new myClrPoint(pa, "Point B",  ptB.x, ptB.y, ptB.z, userPtRad));
        //linear interp
        usrPts.add(new myClrPoint(pa, "Point C", new myPoint(ptA, .5, ptB), userPtRad));    
        ptsChanged();
    }

    
    //convert to ramp local coords
    public myPoint transToRamp(myPoint p){    return myPoint._mult(p, pa.P(1.0f/scaleVals[0],1.0f/scaleVals[1],1.0f/scaleVals[2]) );    }
    //public myPoint transFromRamp(myPoint p){    return myPoint._sub(myPoint._mult(p, pa.P(1.0f/scaleVals[0],1.0f/scaleVals[1],1.0f/scaleVals[2]) ), pa.P(pa.sceneOriginVals[pa.sceneIDX]));    }
    //raycast from mouse to see if clicking on clickable object
    public int checkClickLoc(myPoint clickLoc, myVector mseDirVec){
        myPoint rmpSpcClkLoc = transToRamp(clickLoc);    
//        pa.msClkStr += "Click vals : mseX = " + String.format("%.5f", mseX) + " mseY = "+ String.format("%.5f", mseY);
        for(int i = 0; i<2; ++i){            
            if(myPoint._dist(rmpSpcClkLoc, usrPts.get(i)) < pa.msClkEps )            {        return i;    }
        }        
        return -1;
    }
    
    public void boundVals(int idx){
        myPoint pt = usrPts.get(idx);
        if(pt.x < 0){pt.x = 0;} else if(pt.x > 255){pt.x = 255;}
        if(pt.y < 0){pt.y = 0;} else if(pt.y > 255){pt.y = 255;}
        if(pt.z < 0){pt.z = 0;} else if(pt.z > 255){pt.z = 255;}
    }
    
    public void setMe(myPoint newLoc,int idx){
        usrPts.get(idx).set(transToRamp(newLoc));
        boundVals (idx);    
        ptsChanged();        
    }
    
    //all calcs necessary when key points changed
    public void ptsChanged(){
        usrPts.get(2).set(new myPoint(usrPts.get(0), .5, usrPts.get(1)));
        for(int i =0; i<numClrPts;++i){boundVals(i);}
        updateGrads();        
    }
    
    //build new gradient displays when points move around
    public void updateGrads(){
        //{"RGB","XYZ","LAB","LCH","User Defined 1","User Defined 2"};RGBIdx = 0, XYZIdx = 1, LABIdx = 2, LCHIdx = 3, USR1 = 4, USR2 = 5;
        //for each grad rebuild myClrPoint Array
        myClrPoint p1 = this.usrPts.get(0),p2 = this.usrPts.get(1);
        clrPtsArrays.clear();
        
        myVector test23, test33;
        myClrPoint pt0,  pt4;
    
        myClrPoint bs01 = new myClrPoint(pa, "kn1",RGBrampPt(p1, p2, .33f) , userPtRad), 
                bs02 = new myClrPoint(pa, "kn1",RGBrampPt(p1, p2, .66f) , userPtRad);

        pt0 = new myClrPoint(pa, "kn1",LCHrampPt(p1, p2, .33f) , userPtRad);
        test23 = pa.V(bs01,pt0);
    
        pt4 = new myClrPoint(pa, "kn2",LCHrampPt(p1, p2, .66f) , userPtRad);
        test33 = pa.V(bs02,pt4);

        pt0._add(myVector._mult(test23, .5f));
        pt4._add(myVector._mult(test33, .5f));
        
        myPoint    ptu;            
        for(float s = 0; s <= 1.0f; s+=sIncr){        
            float t = PApplet.pow(PApplet.sin(s*PConstants.HALF_PI), 2);
            ptu = pa.bezierPoint(new myPoint[] {pa.P(p1),pt0,pt4,pa.P(p2)}, t);
            clrPtsArrays.add(new myClrPoint(pa, "USR3",ptu, clrPtRad));
        }    

        //for(int i =0; i<numGrads; ++i){
        grads.rebuildClrs((myClrPoint[])(clrPtsArrays).toArray(new myClrPoint[0]));
        //}        
    }//
    
//    public void drawGradients(){
//        pa.pushMatrix();pa.pushStyle();
//        if(pa.flags[pa.clrAll]){for(int i =0; i<this.grads.length; ++i){grads[i].drawMe(); }} 
//        else {
//        for(int i =0; i<this.grads.length; ++i){if(pa.flags[pa.clrRGB+i]){grads[i].drawMe(); }}}
//        pa.popStyle();pa.popMatrix();
//    }//drawGradientRectangle
    
    public void showDebugOutput(){
        double[] lab0 = RGBtoLAB(R0,G0,B0), lab1 = RGBtoLAB(R1,G1,B1);         
        pa.outStr2Scr(String.format("Mode: %s   k: %d   L: %d\nColor0: RGB(%3d,%3d,%3d) Lab(%3.2f,%3.2f,%3.2f)\nColor1: RGB(%3d,%3d,%3d) Lab(%3.2f,%3.2f,%3.2f)",
                                mode.toUpperCase(),k,layer.l,R0,G0,B0,lab0[0],lab0[1],lab0[2],R1,G1,B1,lab1[0],lab1[1],lab1[2]));
    }
    
    public myClrPoint XYZtoPoint(double X, double Y, double Z) {int [] C= XYZtoRGB(X,Y,Z); return new myClrPoint(pa, "XYZ_Pt",C[0],C[1],C[2],clrPtRad); }
    public myClrPoint LCHtoPoint(double L, double c, double h) {int [] C= LCHtoRGB(L,c,h); return new myClrPoint(pa, "LCH_Pt",C[0],C[1],C[2],clrPtRad); }
    public myClrPoint LABtoPoint(double L, double a, double b) {int [] C= LABtoRGB(L,a,b); return new myClrPoint(pa, "LAB_Pt",C[0],C[1],C[2],clrPtRad); }
    
    public myClrPoint RGBrampPt(myPoint p1, myPoint p2, float s) {return RGBrampPt((int)p1.x, (int)p1.y, (int)p1.z,(int)p2.x, (int)p2.y, (int)p2.z,s); }
    public myClrPoint XYZrampPt(myPoint p1, myPoint p2, float s) {return XYZrampPt((int)p1.x, (int)p1.y, (int)p1.z,(int)p2.x, (int)p2.y, (int)p2.z,s); }
    public myClrPoint LABrampPt(myPoint p1, myPoint p2, float s) {return LABrampPt((int)p1.x, (int)p1.y, (int)p1.z,(int)p2.x, (int)p2.y, (int)p2.z,s); }
    public myClrPoint LCHrampPt(myPoint p1, myPoint p2, float s) {return LCHrampPt((int)p1.x, (int)p1.y, (int)p1.z,(int)p2.x, (int)p2.y, (int)p2.z,s); }
    public myClrPoint USR1rampPt(myPoint p1, myPoint p2, float s) {return USR1rampPt((int)p1.x, (int)p1.y, (int)p1.z,(int)p2.x, (int)p2.y, (int)p2.z,s); }
    public myClrPoint USR2rampPt(myPoint p1, myPoint p2, float s) {return USR2rampPt((int)p1.x, (int)p1.y, (int)p1.z,(int)p2.x, (int)p2.y, (int)p2.z,s); }
    public myClrPoint USR3rampPt(myPoint p1, myPoint p2, float s) {return USR3rampPt((int)p1.x, (int)p1.y, (int)p1.z,(int)p2.x, (int)p2.y, (int)p2.z,s); }

    public myClrPoint RGBrampPt(int R0, int G0, int B0, int R1, int G1, int B1, float s) {return new myClrPoint(pa, "RGB_Pt",(1.f-s)*R0+s*R1,(1.f-s)*G0+s*G1,(1.f-s)*B0+s*B1,clrPtRad); }
    public myClrPoint USR1rampPt(int R0, int G0, int B0, int R1, int G1, int B1, float s) {
        double[] XYZ0 = RGBtoXYZ(R0, G0, B0), XYZ1 = RGBtoXYZ(R1, G1, B1);
        return XYZtoPoint((1.f-s)*XYZ0[0]+s*XYZ1[0],(1.f-s)*XYZ0[1]+s*XYZ1[1],(1.f-s)*XYZ0[2]+s*XYZ1[2]); 
    }
    public myClrPoint USR2rampPt(int R0, int G0, int B0, int R1, int G1, int B1, float s) {
        double[] LAB0 = RGBtoLAB(R0, G0, B0), LAB1 = RGBtoLAB(R1, G1, B1);
        return LABtoPoint((1.f-s)*LAB0[0]+s*LAB1[0],(1.f-s)*LAB0[1]+s*LAB1[1],(1.f-s)*LAB0[2]+s*LAB1[2]); 
    } 

    public myClrPoint USR3rampPt(int R0, int G0, int B0, int R1, int G1, int B1, float s) {
        double[] LCH0 = RGBtoLCH(R0, G0, B0), LCH1 = RGBtoLCH(R1, G1, B1);
        double d = LCH1[2] - LCH0[2];
        d = d > 180 ? d - 360 : d;
        return LCHtoPoint((1.f-s)*LCH0[0]+s*LCH1[0],(1.f-s)*LCH0[1]+s*LCH1[1], LCH0[2]+s*d ); 
    } 
    
    public myClrPoint XYZrampPt(int R0, int G0, int B0, int R1, int G1, int B1, float s) {
        double[] XYZ0 = RGBtoXYZ(R0, G0, B0), XYZ1 = RGBtoXYZ(R1, G1, B1);
        return XYZtoPoint((1.f-s)*XYZ0[0]+s*XYZ1[0],(1.f-s)*XYZ0[1]+s*XYZ1[1],(1.f-s)*XYZ0[2]+s*XYZ1[2]); 
    }
    public myClrPoint LABrampPt(int R0, int G0, int B0, int R1, int G1, int B1, float s) {
        double[] LAB0 = RGBtoLAB(R0, G0, B0), LAB1 = RGBtoLAB(R1, G1, B1);
        return LABtoPoint((1.f-s)*LAB0[0]+s*LAB1[0],(1.f-s)*LAB0[1]+s*LAB1[1],(1.f-s)*LAB0[2]+s*LAB1[2]); 
    } 

    public myClrPoint LCHrampPt(int R0, int G0, int B0, int R1, int G1, int B1, float s) {
        double[] LCH0 = RGBtoLCH(R0, G0, B0), LCH1 = RGBtoLCH(R1, G1, B1);
        double d = LCH1[2] - LCH0[2];
        d = d > 180 ? d - 360 : d;
        return LCHtoPoint((1.f-s)*LCH0[0]+s*LCH1[0],(1.f-s)*LCH0[1]+s*LCH1[1], LCH0[2]+s*d ); 
      } 
    // LAB to color
    public int LABtoColor(double L, double a, double b) {int [] C= LABtoRGB(L,a,b); return pa.color(C[0],C[1],C[2]);}  
    
    // LAB > RGB = (LAB > XYZ) + (XYZ > RGB)
    public int[] LABtoRGB(double L, double a, double b) {return XYZtoRGB(LABtoXYZ(L, a, b));}
    
    // LAB > XYZ
    public double[] LABtoXYZ(double[] LAB) {return LABtoXYZ(LAB[0], LAB[1], LAB[2]);}
    public double[] LABtoXYZ(double L, double a, double b) {
        double[] result = new double[3];
        double y = (L + 16.0f) / 116.0f, y3 = Math.pow(y, 3.0f);
        double x = (a / 500.0f) + y, x3 = Math.pow(x, 3.0f);
        double z = y - (b / 200.0f), z3 = Math.pow(z, 3.0f);
        if (y3 > 0.008856f) y = y3; else y = (y - (16.0f / 116.0f)) / 7.787f;
        if (x3 > 0.008856f) x = x3; else x = (x - (16.0f / 116.0f)) / 7.787f;
        if (z3 > 0.008856f) z = z3; else z = (z - (16.0f / 116.0f)) / 7.787f;
        result[0] = x * whitePoint[0];
        result[1] = y * whitePoint[1];
        result[2] = z * whitePoint[2];
        return result;
    }
    
    // LAB > LCH (from http://www.brucelindbloom.com/index.html?Equations.html)
    public double[] LABtoLCH(double[] LAB) {return LABtoLCH(LAB[0], LAB[1], LAB[2]);}
    public double[] LABtoLCH(double L, double a, double b) {
        double[] result = new double[3];
        double h = Math.atan2(b, a);
        // convert radians to degrees
        if (h > 0) h = Math.toDegrees(h);
        else if (h < 0) h = 360 - Math.toDegrees(Math.abs(h));
        if (h < 0) h += 360;
        else if (h >= 360) h -= 360;
        result[0] = L;
        result[1] = Math.sqrt(a*a + b*b);
        result[2] = h;
        return result;
    }
    
    // LCH to color
    public int LCHtoColor(double L, double c, double h) {int [] C= LCHtoRGB(L,c,h); return pa.color(C[0],C[1],C[2]);}
    
    // LCH > RGB = (LCH > LAB) + (LAB > XYZ) + (XYZ > RGB)
    public int[] LCHtoRGB(double L, double c, double h) {return XYZtoRGB(LABtoXYZ(LCHtoLAB(L, c, h)));}
    
    // LCH > LAB (from http://www.brucelindbloom.com/index.html?Equations.html)
    public double[] LCHtoLAB(double L, double c, double h) {
        h = Math.toRadians(h);
        return new double[] {L,c * Math.cos(h), c * Math.sin(h)};
    }
    
    // XYZ to color
    public int XYZtoColor(double X, double Y, double Z) {int [] C= XYZtoRGB(X,Y,Z); return pa.color(C[0],C[1],C[2]);}
    
    // XYZ > RGB  
    public int calcXYZtoRGB(double c) {if (c>0.0031308f) {c=((1.055f*Math.pow(c,1.0f/2.4f))-0.055f);} else {c=(c*12.92f);}  return (int)((c < 0) ? 0 : ((c > 1) ? 1 : c));}
    public int[] XYZtoRGB(double[] XYZ) {return XYZtoRGB(XYZ[0], XYZ[1], XYZ[2]);}
    public int[] XYZtoRGB(double X, double Y, double Z) {
        double x = X / 100.0f;
        double y = Y / 100.0f;
        double z = Z / 100.0f;
        // [r g b] = [X Y Z][Mi]
        double r = (x * Mi[0][0]) + (y * Mi[0][1]) + (z * Mi[0][2]);
        double g = (x * Mi[1][0]) + (y * Mi[1][1]) + (z * Mi[1][2]);
        double b = (x * Mi[2][0]) + (y * Mi[2][1]) + (z * Mi[2][2]);
        return new int[] {calcXYZtoRGB(r),calcXYZtoRGB(g),calcXYZtoRGB(b)};
    }
     
        
    // RGB > LAB = (RGB > XYZ) + (XYZ > LAB)
    public double[] RGBtoLAB(int R, int G, int B) {return XYZtoLAB(RGBtoXYZ(R, G, B));}
    
    // RGB > LCH = (RGB > XYZ) + (XYZ > LAB) + (LAB > LCH)
    public double[] RGBtoLCH(int R, int G, int B) {return LABtoLCH(XYZtoLAB(RGBtoXYZ(R, G, B)));}
    
    // RGB > XYZ
    private double calcRGBtoXYZ(int C) {double c = C/255.0f;if (c <= 0.04045f) {c = c/12.92f;} else {c = Math.pow(((c+0.055f)/1.055f), 2.4f);}return c*100.0;}
    public double[] RGBtoXYZ(int R, int G, int B) {
        // convert 0..255 into 0..1
        double r = calcRGBtoXYZ(R);// R / 255.0f;
        double g = calcRGBtoXYZ(G);// G / 255.0f;
        double b = calcRGBtoXYZ(B);// B / 255.0f;
//        // assume sRGB
//        if (r <= 0.04045f) {r = r / 12.92f;} else {r = Math.pow(((r + 0.055f) / 1.055f), 2.4f);}
//        if (g <= 0.04045f) {g = g / 12.92f;} else {g = Math.pow(((g + 0.055f) / 1.055f), 2.4f);}
//        if (b <= 0.04045f) {b = b / 12.92f;} else {b = Math.pow(((b + 0.055f) / 1.055f), 2.4f);}
//        r *= 100.0f;
//        g *= 100.0f;
//        b *= 100.0f;
        // [X Y Z] = [r g b][M]
        return new double[] {(r * M[0][0]) + (g * M[0][1]) + (b * M[0][2]),(r * M[1][0]) + (g * M[1][1]) + (b * M[1][2]),(r * M[2][0]) + (g * M[2][1]) + (b * M[2][2]) };
    }
    
    // XYZ > LAB
    public double[] XYZtoLAB(double[] XYZ) {return XYZtoLAB(XYZ[0], XYZ[1], XYZ[2]);}
    public double[] XYZtoLAB(double X, double Y, double Z) {
        double x = X / whitePoint[0], y = Y / whitePoint[1], z = Z / whitePoint[2];
        if (x > 0.008856f) x = Math.pow(x, 1.0f / 3.0f); else x = (7.787f * x) + (16.0f / 116.0f);
        if (y > 0.008856f) y = Math.pow(y, 1.0f / 3.0f); else y = (7.787f * y) + (16.0f / 116.0f);
        if (z > 0.008856f) z = Math.pow(z, 1.0f / 3.0f); else z = (7.787f * z) + (16.0f / 116.0f);
        return new double[] {(116.0f * y) - 16.0f,500.0f * (x - y),200.0f * (y - z)};
    }
    
    public int RGBramp(myPoint p1, myPoint p2, float s) {return RGBramp((int)p1.x, (int)p1.z, (int)p1.y,(int)p2.x, (int)p2.z, (int)p2.y,s); }
    public int XYZramp(myPoint p1, myPoint p2, float s) {return XYZramp((int)p1.x, (int)p1.z, (int)p1.y,(int)p2.x, (int)p2.z, (int)p2.y,s); }
    public int LABramp(myPoint p1, myPoint p2, float s) {return LABramp((int)p1.x, (int)p1.z, (int)p1.y,(int)p2.x, (int)p2.z, (int)p2.y,s); }
    public int LCHramp(myPoint p1, myPoint p2, float s) {return LCHramp((int)p1.x, (int)p1.z, (int)p1.y,(int)p2.x, (int)p2.z, (int)p2.y,s); }

    public int RGBramp(int R0, int G0, int B0, int R1, int G1, int B1, float s) {return pa.color((1.f-s)*R0+s*R1,(1.f-s)*G0+s*G1,(1.f-s)*B0+s*B1); }
    public int XYZramp(int R0, int G0, int B0, int R1, int G1, int B1, float s) {
        double[] XYZ0 = RGBtoXYZ(R0, G0, B0), XYZ1 = RGBtoXYZ(R1, G1, B1);
        return XYZtoColor((1.f-s)*XYZ0[0]+s*XYZ1[0],(1.f-s)*XYZ0[1]+s*XYZ1[1],(1.f-s)*XYZ0[2]+s*XYZ1[2]); 
    }
    public int LABramp(int R0, int G0, int B0, int R1, int G1, int B1, float s) {
        double[] LAB0 = RGBtoLAB(R0, G0, B0), LAB1 = RGBtoLAB(R1, G1, B1);
        return LABtoColor((1.f-s)*LAB0[0]+s*LAB1[0],(1.f-s)*LAB0[1]+s*LAB1[1],(1.f-s)*LAB0[2]+s*LAB1[2]); 
    } 

    public int LCHramp(int R0, int G0, int B0, int R1, int G1, int B1, float s) {
        double[] LCH0 = RGBtoLCH(R0, G0, B0), LCH1 = RGBtoLCH(R1, G1, B1);
        double d = LCH1[2] - LCH0[2];
        d = d > 180 ? d - 360 : d;
        return LCHtoColor((1.f-s)*LCH0[0]+s*LCH1[0],(1.f-s)*LCH0[1]+s*LCH1[1], LCH0[2]+s*d ); 
    } 
    // ***************** Jarek's tools for drawing ramps
    
    public void showColor(int c, float r) {myPoint C = PofColor(c); pa.fill(c); pa.show(C,r,-1);}
    public void showColor(myPoint p, float r){int c = pa.color(p); pa.fill(c); pa.show(p,r,-1);}
    public void showColor(myPoint p, float r, String txt){int c = pa.color(p); pa.fill(c); pa.show(p,r,-1,txt);}
    public void showColor(int c, float r, String txt) {myPoint C = PofColor(c); pa.fill(c); pa.show(C,r,-1,txt);}
    public myPoint PofColor(int c) {return pa.P(pa.red(c), pa.green(c), pa.blue(c));}
    public int redC(int c) {return PApplet.parseInt (c >> 16 & 0xFF); } 
    public int greenC(int c) {return PApplet.parseInt (c >> 8 & 0xFF); }  
    public int blueC(int c) {return PApplet.parseInt (c & 0xFF); } 
    
    public static float da = PConstants.TWO_PI/12.0f;
    public void cylinder(int c1, int c2) {
        myPoint C1 = PofColor(c1),C2 = PofColor(c2),P = C1;
        myVector V = pa.V(C1,C2),I = pa.U(Normal(V)),J = pa.U(pa.N(I,V));
        pa.beginShape(PConstants.QUAD_STRIP);
        double bcA, bsA;
            for(float a=0; a<=PConstants.TWO_PI+da; a+=da) {
                bcA = barThick*Math.cos(a); bsA = barThick*Math.sin(a);
                pa.fill(c1); pa.gl_vertex(pa.P(P,bcA,I,bsA,J,0,V)); 
                pa.fill(c2); pa.gl_vertex(pa.P(P,bcA,I,bsA,J,1,V));
            }
        pa.endShape();
    }
    public myVector Normal(myVector V) {
        if(Math.abs(V.z)<=Math.min(Math.abs(V.x),Math.abs(V.y))) return pa.V(-V.y,V.x,0); 
        if(Math.abs(V.x)<=Math.min(Math.abs(V.z),Math.abs(V.y))) return pa.V(0,-V.z,V.y);
        return pa.V(V.z,0,-V.x);
    }  
    
    public void drawLabRamp(int c0, int c1) {
        int rc0 = redC(c0), rc1 = redC(c1), bc0=blueC(c0), bc1=blueC(c1), gc0=greenC(c0), gc1=greenC(c1);
        for(float s=0; s<=1; s+=0.01f) {showColor(LABramp( rc0, gc0, bc0,  rc1, gc1, bc1, s),5);}
    }
    
    public void drawRGBRamp(int c0, int c1) {
        int rc0 = redC(c0), rc1 = redC(c1), bc0=blueC(c0), bc1=blueC(c1), gc0=greenC(c0), gc1=greenC(c1);
        for(float s=0; s<=1; s+=0.01f) {showColor(RGBramp( rc0, gc0, bc0,  rc1, gc1, bc1, s),5);} 
    }
    
    // ***************** Paco's tools for drawing ramps
    public int[] rampColors(int r0, int g0, int b0, int r1, int g1, int b1, int r, String mode) {
        mode = mode.toLowerCase().trim();
        int[] colors = new int[r];
        myPoint[] points = new myPoint[r];
        float step = 1.f/(r-1);
        if (mode.equals("rgb")) {for (int i=0; i<r; ++i) {colors[i] = RGBramp(r0,g0,b0,r1,g1,b1, i*step);}}
        else if (mode.equals("xyz")) {for (int i=0; i<r; ++i) {colors[i] = XYZramp(r0,g0,b0,r1,g1,b1, i*step);}}
        else if (mode.equals("lab")) {for (int i=0; i<r; ++i) {colors[i] = LABramp(r0,g0,b0,r1,g1,b1, i*step);}}
          else if (mode.equals("lch")) {for (int i=0; i<r; ++i) {colors[i] = LCHramp(r0,g0,b0,r1,g1,b1, i*step);}}
        return colors;
    }
      
    public myPoint[] rampPoints(int[] colors) {
        myPoint[] points = new myPoint[colors.length];
        for (int i=0; i<colors.length; ++i) {points[i] = new myPoint(300.0f*pa.red(colors[i])/255.0f, 300.0f*pa.blue(colors[i])/255.0f, 300.0f*pa.green(colors[i])/255.0f);}
        return points;
    }
    
    public void drawRamp(int[] colors, myPoint[] points) {
        for (int i=0; i<colors.length; ++i) {pa.fill(colors[i]); pa.show(points[i],5);}
    }
      
    
    // ***************** Paco's tools for showing iso-surface layers of different color spaces 
    public class Layer {
        int l, sample;
        myPoint[][] P;
        int[][] C;
      
        public Layer(int l, int sample) {
            this.l=l; this.sample=sample;
            P = new myPoint[sample][sample];
            C = new int[sample][sample];
            calculate();
        }
        
        public void calculate() {
            int[] rgb = new int[3];
            for(int n=0; n<sample; ++n) {
                for(int m=0; m<sample; ++m) {
                    float i=(float)n/sample, j=(float)m/sample;
                    if(mode.equals("xyz")) {rgb=XYZtoRGB(100*i, l, 100*j);}
                    else if(mode.equals("lab")) {rgb=LABtoRGB(l, 255*i-128, 255*j-128);}
                    else if(mode.equals("lch")) {rgb=LCHtoRGB(l, 100*i, 360*j);}
                    P[n][m] = pa.P(300*rgb[0]/255,300*rgb[2]/255,300*rgb[1]/255);
                    C[n][m] = pa.color(rgb[0], rgb[1], rgb[2]);
                }
            }
        }
    }//class Layer
    public void show(Layer l) {
        for(int i=0; i<l.sample; ++i) {for(int j=0; j<l.sample; ++j) {pa.fill(l.C[i][j]); pa.show(l.P[i][j], 5);}}
        pa.strokeWeight(1); pa.stroke(0xff000000);
        for(int i=0; i<l.sample; ++i) {for(int j=0; j<l.sample-1; ++j) {pa.line(l.P[i][j].x, l.P[i][j].y, l.P[i][j].z, l.P[i][j+1].x, l.P[i][j+1].y, l.P[i][j+1].z);}}
        for(int i=0; i<l.sample-1; ++i) {for(int j=0; j<l.sample; ++j) {pa.line(l.P[i][j].x, l.P[i][j].y, l.P[i][j].z, l.P[i+1][j].x, l.P[i+1][j].y, l.P[i+1][j].z);}}
    }

}//class myClrRamp


class myClrGrad{
    public DancingBalls pa;
    public myClrRamp rmp;
    public String name;
    public float x,y, w,h;
    public myClrPoint[] clrs;                //all colors in this gradient - r = x, g = z, b = y

    public myClrGrad(DancingBalls _pa, myClrRamp _rmp, float _x, float _y, String _name){
        pa = _pa;
        rmp = _rmp;
        name = _name;
        w = pa.width * .13f; 
        h = pa.height * .07f;

        x = _x;
        y = _y;
        clrs = new myClrPoint[255];
        for(int i =0; i < 255; ++i){clrs[i] = new myClrPoint(pa, name+"pt:"+i, 255-i,i,0,rmp.clrPtRad);}
    }
    //take an array of values and map it to this clrs array
    public void rebuildClrs(myClrPoint[] _clrs){
        clrs = _clrs;
//        clrs = new myClrPoint[_clrs.length];
//        System.arraycopy(_clrs, 0, clrs, 0, _clrs.length);
    }
    
    public void drawMe(){
        pa.pushMatrix();pa.pushStyle();    
        pa.fill(0,0,0,255);
        pa.text(name, x, y-10);
        pa.rect(x, y, w, h);
        float offset = w/(1.0f * clrs.length);            //how much to move laterally in ramp for each color
        float st = x;
        for(int i = 0; i< clrs.length;++i){
            pa.fillAndShowLineByRBGPt(clrs[i],st, y, offset, h);//(myPoint p, float x,  float y, float w, float h)
            st += offset;
        }
        pa.fill(0,0,0,255);
        pa.text("3rd Color",x-100, y-10);
        pa.fillAndShowLineByRBGPt(clrs[clrs.length/2],x-100, y, h, h);
        pa.rect(x-100, y, h, h);
        pa.popStyle();pa.popMatrix();
    }//drawGradientRectangle    
    
    public void drawSpline(){
        pa.pushMatrix();pa.pushStyle();
        for(int i = 0; i< clrs.length;++i){    rmp.showColor(clrs[i],clrs[i].rad);}        
        pa.popStyle();pa.popMatrix();    
    }
}//class myClrGradient
    
class myClrPoint extends myPoint{
    public DancingBalls pa;    
    public String name;
    public float rad;
    
    public myClrPoint(DancingBalls _pa, String _nm, double _red, double _green, double _blue, double r){
        super(_red, _green, _blue);
        pa = _pa;
        name = _nm;
        rad = (float)r;
    }    
    public myClrPoint(DancingBalls _pa, String _nm, myPoint pt, double r){
        super(pt);
        pa = _pa;
        name = _nm;
        rad = (float)r;
    }    
    
    public void showColor(){int c = pa.color(this); pa.fill(c); pa.show(this,rad,-1,toString());}
    public String toString(){
        String res = ""+ name + " R :"+x+" G : " + z  + " B : " + y;
        return res;
    }
}//myClrPoint
