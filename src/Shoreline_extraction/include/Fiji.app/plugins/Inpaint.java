import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
 
import java.util.ArrayList;
import java.util.List;
 
/**
 *  InPaint by isophote continuation 
 * 
 * @author Xavier Philippeau
 *
 */
public class Inpaint_ implements PlugInFilter {
 
	// Temporary workspace
	private class Channel {
		private int[][] data;
		public Channel(int w,int h) {
			data = new int[h][w];
		}
		public int getValue(int x, int y) {
			return data[y][x];
		}
		public void setValue(int x, int y, int v) {
			data[y][x]=v;
		}
	}
 
	// neighbours offsets (for border spreading)
	private int[] dx4 = new int[] {-1, 0, 1, 0};
	private int[] dy4 = new int[] { 0,-1, 0, 1};
 
    // neighbours offsets (for sampling)
	private int[] dxs = null;
	private int[] dys = null;
 
	// distance Map to the unmasked part of the image
	private Channel distmap = null;
 
	// Output image
	private Channel output = null;
	private int width = 0;
	private int height = 0;
 
	// mask color
	private int[] maskcolor = new int[3];
 
	// isophote preservation factor
	private int preservation = 0;
 
	// About...
	private void showAbout() {
		IJ.showMessage("InPaint...","InPaint Filter by Pseudocode");
	}
 
	public int setup(String arg, ImagePlus imp) {
 
		// about...
		if (arg.equals("about")) {
			showAbout(); 
			return DONE;
		}
 
		// else...
		if (imp==null) return DONE;
 
		// Configuration dialog.
		GenericDialog gd = new GenericDialog("Parameters");
		gd.addNumericField("Sample region size",24,0);
		gd.addStringField("Mask color (R,G,B)","255,0,0");
		gd.addNumericField("Isophote preservation factor",4,0);
 
		int nmbsample = 0;
		String hexamask ="";
		gd.showDialog();
		while(true) {
			if ( gd.wasCanceled() )	return DONE;
 
			nmbsample = (int) gd.getNextNumber();
			hexamask = gd.getNextString();
			this.preservation = (int) gd.getNextNumber();
 
			if (nmbsample<=0) continue;
			if (this.preservation<0) continue;
			if (hexamask.split(",").length!=3) continue;
 
			break;
		}
		gd.dispose();
 
		// Get Mask Color
		String[] split = hexamask.split(",");
		this.maskcolor[0] = Integer.parseInt(split[0]);
		this.maskcolor[1] = Integer.parseInt(split[1]);
		this.maskcolor[2] = Integer.parseInt(split[2]);
 
		// Initialize dxs[] and dys[] tables 
		initSample(nmbsample);
 
		return PlugInFilter.DOES_RGB;
	}
 
	private void initSample(int nmbsample) {
		// Initialize neighbours offsets for sampling 
		dxs = new int[nmbsample];
		dys = new int[nmbsample];
 
		// **** build a spiral curve ****  
 
		// directions: Left=(-1,0) Up=(0,-1) Right=(1,0) Down=(0,1)
		int[] dx = new int[] {-1, 0,1,0};
		int[] dy = new int[] { 0,-1,0,1};
		int dirIndex=0;
		int distance=0;
		int stepToDo=1;
		int x=0, y=0;
		while (true) {
			// move two times with the same StepCount
			for (int i = 0; i < 2; i++) {
				// move
				for (int j = 0; j < stepToDo; j++) {
					x += dx[dirIndex];
					y += dy[dirIndex];
					dxs[distance] = x;
					dys[distance] = y;
					distance++;
					if (distance >= nmbsample) return;
				}
				// turn right
				dirIndex = (dirIndex + 1) % 4;
			}
			// increment StepCount
			stepToDo++;
		}
	}
 
	public void run(ImageProcessor ip) {
 
		// ImageProcessor -> GRAYLEVEL IMAGE
		ByteProcessor input = new ByteProcessor(ip.getWidth(),ip.getHeight());
 
		// ImageProcessor -> BINARY MASK
		ByteProcessor mask = new ByteProcessor(ip.getWidth(),ip.getHeight());
 
		for (int y = 0; y < ip.getHeight(); y++) {
			for (int x = 0; x < ip.getWidth(); x++) {
				int[] rgb = ip.getPixel(x,y,null);
 
				int gray = (rgb[0]+rgb[1]+rgb[2])/3;
				input.set(x,y,gray);
 
				if (rgb[0]==this.maskcolor[0] && rgb[1]==this.maskcolor[1] && rgb[2]==this.maskcolor[2])
					mask.set(x,y,255);
				else
					mask.set(x,y,0);
			}
		}
 
		// Inpaint filter
		inpaintloop(input, mask);
 
		// ByteProcessor -> ImageProcessor conversion
		ImageProcessor result = new ByteProcessor(ip.getWidth(),ip.getHeight());
		for (int y = 0; y < ip.getHeight(); y++) {
			for (int x = 0; x < ip.getWidth(); x++) {
				result.set(x,y,this.output.getValue(x,y));
			}
		}
		ImagePlus newImg = new ImagePlus("Inpaint Filter Result", result);
		newImg.show();
 
	}
 
	// ---------------------------------------------------------------------------------
 
 
	// Compute the initial borderline (unmasked pixels close to the mask)
	private List<int[]> computeBorderline(ByteProcessor mask) {
 
		List<int[]> borderline = new ArrayList<int[]>();
 
		for (int y=0; y<this.height; y++) {
			for (int x=0; x<this.width; x++) {
				// for each pixel NOT masked
				int v = mask.get(x,y);
				if (v>127) continue;
 
				// if a neighboor is masked
				// => put the pixel in the borderline list
				for (int k=0; k<4; k++) {
					int xk = x+dx4[k];
					int yk = y+dy4[k];
					if (xk<0 || xk>=this.width) continue;
					if (yk<0 || yk>=this.height) continue;
					int vk = mask.get(xk,yk);
					if (vk>127) {
						borderline.add(new int[] {x,y});
						break;
					}
				}
			}
		}
		return borderline;
	}
 
	// iteratively inpaint the image
	private void inpaintloop(ByteProcessor input, ByteProcessor mask) {
		this.width = input.getWidth();
		this.height = input.getHeight();
 
		// initialize output image
		this.output = new Channel(this.width,this.height);
		for (int y=0; y<this.height; y++) {
			for (int x=0; x<this.width; x++) {
				if (mask.get(x, y)<127)
					this.output.setValue(x, y, input.get(x, y)); // known value
				else
					this.output.setValue(x, y, -1); // unknown value (masked)
			}
		}
 
		// initialize the distance map
		this.distmap = new Channel(this.width,this.height);
		for (int y=0; y<this.height; y++)
			for (int x=0; x<this.width; x++)
				if (mask.get(x, y)<127)
					this.distmap.setValue(x, y, 0); // outside the mask -> distance = 0
				else
					this.distmap.setValue(x, y, Integer.MAX_VALUE); // inside the mask -> distance unknown
 
		// outer borderline 
		List<int[]> borderline = computeBorderline(mask);
 
		// iteratively reduce the borderline
		while(!borderline.isEmpty()) {
			borderline = propagateBorderline(borderline);
		}
	}
 
	// inpaint pixels close to the borderline 
	private List<int[]> propagateBorderline(List<int[]> boderline) {
 
		List<int[]> newBorderline = new ArrayList<int[]>();
 
		// for each pixel in the bordeline
		for (int[] pixel : boderline) {
			int x=pixel[0];
			int y=pixel[1];
 
			// distance from the image
			int dist = this.distmap.getValue(x, y);
 
			// explore neighbours, search for uncomputed pixels
			for (int k=0; k<4; k++) {
				int xk = x+dx4[k];
				int yk = y+dy4[k];
				if (xk<0 || xk>=this.width) continue;
				if (yk<0 || yk>=this.height) continue;
 
				int vout = this.output.getValue(xk,yk);
				if (vout>=0) continue; // pixel value is already known.
 
				// compute distance to image 
				this.distmap.setValue(xk, yk, dist+1);
 
				// inpaint this pixel
				int v = inpaint(xk, yk);
				if (v<0) { 
					// should not happen.
					System.err.println("inpaint for "+xk+","+yk+" returns "+v);
					this.output.setValue(xk, yk, v);
					continue; 
				}
				this.output.setValue(xk, yk, v);
 
				// add this pixel to the new borderline
				newBorderline.add( new int[]{xk,yk} );
			}
		}
 
		return newBorderline;
	}
 
	// inpaint one pixel
	private int inpaint(int x, int y) {
 
		double wsum = 0;
		double vinpaint = 0;
 
		int dist = this.distmap.getValue(x, y);
 
		// sampling pixels in the region
		List<int[]> region = new ArrayList<int[]>(); 
		for (int k=0; k<dxs.length; k++) {
			int xk = x+dxs[k];
			int yk = y+dys[k];
			if (xk<0 ||xk>=this.width) continue;
			if (yk<0 ||yk>=this.height) continue;
 
			// take only pixels computed in previous loops
			int distk = this.distmap.getValue(xk, yk);
			if (distk>=dist) continue;
 
			region.add( new int[]{xk,yk} );
		}
 
		// mean isophote vector of the region
		double isox = 0, isoy = 0;
		int count=0;
		for (int[] pixel: region) {
			int xk = pixel[0];
			int yk = pixel[1];
 
			// isophote direction = normal to the gradient 
			double[] g = gradient(xk,yk,dist);
			if (g!=null){
				isox += -g[1] * g[2];
				isoy += g[0] * g[2];
				count++;
			}
		}
		if (count>0) {
			isox/=count; isoy/=count;  
		}
		double isolength = Math.sqrt( isox*isox + isoy*isoy );
 
		// contribution of each pixels in the region
		for (int[] pixel: region) {
			int xk = pixel[0];
			int yk = pixel[1];
 
			// propagation vector
			int px = x-xk;
			int py = y-yk;
			double plength = Math.sqrt( px*px + py*py );
 
			// Weight of the propagation:
 
			// 1. isophote continuation: cos(isophote,propagation) = normalized dot product ( isophote , propagation ) 
			double wisophote = 0;
			if (isolength>0) {
				double cosangle = Math.abs(isox*px+isoy*py) / (isolength*plength);
				cosangle = Math.min(cosangle, 1.0);
				/*
				// linear weight version:
				double angle = Math.acos(cosangle);
				double alpha = 1-(angle/Math.PI);
				wisophote = Math.pow(alpha,this.preservation);
				*/
				wisophote = Math.pow(cosangle,this.preservation);
			}
 
			// 2. spread direction: 
			// gradient length = O -> omnidirectionnal
			// gradient length = maxlength -> unidirectionnal
			double unidir = Math.min(isolength/255,1);
 
			// 3. distance: distance to inpaint pixel
			double wdist = 1.0 / (1.0 + plength*plength);
 
			// 4. probability: distance to image (unmasked pixel)
			int distk = this.distmap.getValue(xk, yk);
			double wproba = 1.0 / (1.0 + distk*distk);
 
			// global weight
			double w = wdist * wproba * ( unidir*wisophote + (1-unidir)*1 );
 
			vinpaint += w*this.output.getValue(xk,yk);
			wsum+=w;
		}
		if (wsum<=0) return -1;
		vinpaint/=wsum;
 
		if (vinpaint<0) vinpaint = 0;
		if (vinpaint>255) vinpaint = 255;
		return (int)vinpaint;
	}
 
	// 8 neightbours gradient
	private double[] gradient(int x, int y, int dist) {
		// Coordinates of 8 neighbours
		int px = x - 1;  // previous x
		int nx = x + 1;  // next x
		int py = y - 1;  // previous y
		int ny = y + 1;  // next y
 
		// limit to image dimension
		if (px < 0)	return null;
		if (nx >= this.width) return null;
		if (py < 0)	return null;
		if (ny >= this.height) return null;
 
		// availability of the 8 neighbours
		// (must be computed in previous loops) 
		if (this.distmap.getValue(px,py)>=dist) return null;
		if (this.distmap.getValue( x,py)>=dist) return null;
		if (this.distmap.getValue(nx,py)>=dist) return null;
		if (this.distmap.getValue(px, y)>=dist) return null;
		if (this.distmap.getValue(nx, y)>=dist) return null;
		if (this.distmap.getValue(px,ny)>=dist) return null;
		if (this.distmap.getValue( x,ny)>=dist) return null;
		if (this.distmap.getValue(nx,ny)>=dist) return null;
 
		// Intensity of the 8 neighbours
		int Ipp = this.output.getValue(px,py);
		int Icp = this.output.getValue( x,py);
		int Inp = this.output.getValue(nx,py);
		int Ipc = this.output.getValue(px, y);
		int Inc = this.output.getValue(nx, y);
		int Ipn = this.output.getValue(px,ny);
		int Icn = this.output.getValue( x,ny);
		int Inn = this.output.getValue(nx,ny);
 
		// Local gradient
		double r2 = 2*Math.sqrt(2);
		double gradx = (Inc-Ipc)/2.0 + (Inn-Ipp)/r2 + (Inp-Ipn)/r2;
		double grady = (Icn-Icp)/2.0 + (Inn-Ipp)/r2 + (Ipn-Inp)/r2;
		double norme = Math.sqrt(gradx*gradx+grady*grady);
 
		return new double[] { gradx, grady, norme };  
	}
 
}