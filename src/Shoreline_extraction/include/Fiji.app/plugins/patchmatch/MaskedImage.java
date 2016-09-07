package patchmatch;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Wrapper/Helper for Masked RGB BufferedImage 
 * 
 * @author Xavier Philippeau
 *
 */
public class MaskedImage {

	// image data
	private boolean[][] mask;
	private BufferedImage image;
	public final int W,H;
	
	// the maximum value returned by MaskedImage.distance() 
	public static final int DSCALE = 65535;
	
	// array for converting distance to similarity
	public static final double[] similarity;

	static {
		// reference array is length 100, but is truncated at first zero value
		// base[0]=1.0, base[1]=0.99, ..., base[99]=0, base[100]=0    
		double[] base = {1.0, 0.99, 0.96, 0.83, 0.38, 0.11, 0.02, 0.005, 0.0006, 0.0001, 0 };
		
		// stretch base array 
		similarity = new double[DSCALE+1];
		for(int i=0;i<similarity.length;i++) {
			double t = (double)i/similarity.length;

			// interpolate from base array values
			int j = (int)(100*t), k=j+1;
			double vj = (j<base.length)?base[j]:0;
			double vk = (k<base.length)?base[k]:0;
			
			double v = vj + (100*t-j)*(vk-vj);
			similarity[i] = v;
		}
	}
	
	// construct from existing BufferedImage and mask
	public MaskedImage(BufferedImage image, boolean[][] mask) {
		this.image = image;
		this.W=image.getWidth();
		this.H=image.getHeight();
		this.mask = mask;
	}

	// construct empty image
	public MaskedImage(int width, int height) {
		this.W=width;
		this.H=height;
		this.image = new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
		this.mask = new boolean[W][H];
	}
		
	public BufferedImage getBufferedImage() {
		return image;
	}
	
	public int getSample(int x, int y, int band) {
		return image.getRaster().getSample(x, y, band);
	}
	
	public void setSample(int x, int y, int band, int value) {
		image.getRaster().setSample(x, y, band, value);
	}

	public boolean isMasked(int x, int y) {
		return mask[x][y];
	}
	
	public void setMask(int x, int y, boolean value) {
		mask[x][y]=value;
	}
	
	// return true if the patch contains one (or more) masked pixel
	public boolean constainsMasked(int x, int y, int S) {
		for(int dy=-S;dy<=S;dy++) {
			for(int dx=-S;dx<=S;dx++) {
				int xs=x+dx, ys=y+dy;
				if (xs<0 || xs>=W) continue;
				if (ys<0 || ys>=H) continue;
				if (mask[xs][ys]) return true;
			}
		}
		return false;
	}	
	
	// distance between two patches in two images
	public static int distance(MaskedImage source,int xs,int ys, MaskedImage target,int xt,int yt, int S) {
		long distance=0, wsum=0, ssdmax = 9*255*255;

		// for each pixel in the source patch
		for(int dy=-S;dy<=S;dy++) {
			for(int dx=-S;dx<=S;dx++) {
				wsum+=ssdmax;
				
				int xks=xs+dx, yks=ys+dy;
				if (xks<1 || xks>=source.W-1) {distance+=ssdmax; continue;}
				if (yks<1 || yks>=source.H-1) {distance+=ssdmax; continue;}
				
				// cannot use masked pixels as a valid source of information
				if (source.isMasked(xks, yks)) {distance+=ssdmax; continue;}
				
				// corresponding pixel in the target patch
				int xkt=xt+dx, ykt=yt+dy;
				if (xkt<1 || xkt>=target.W-1) {distance+=ssdmax; continue;}
				if (ykt<1 || ykt>=target.H-1) {distance+=ssdmax; continue;}

				// cannot use masked pixels as a valid source of information
				if (target.isMasked(xkt, ykt)) {distance+=ssdmax; continue;}
				
				// SSD distance between pixels (each value is in [0,255^2])
				long ssd=0;
				for(int band=0; band<3; band++) {
					// pixel values
					int s_value = source.getSample(xks, yks, band);
					int t_value = source.getSample(xkt, ykt, band);
					
					// pixel horizontal gradients (Gx)
					int s_gx = 128+(source.getSample(xks+1, yks, band) - source.getSample(xks-1, yks, band))/2;
					int t_gx = 128+(target.getSample(xkt+1, ykt, band) - target.getSample(xkt-1, ykt, band))/2;

					// pixel vertical gradients (Gy)
					int s_gy = 128+(source.getSample(xks, yks+1, band) - source.getSample(xks, yks-1, band))/2;
					int t_gy = 128+(target.getSample(xkt, ykt+1, band) - target.getSample(xkt, ykt-1, band))/2;

					ssd += Math.pow(s_value-t_value , 2); // distance between values in [0,255^2]
					ssd += Math.pow(s_gx-t_gx , 2); // distance between Gx in [0,255^2]
					ssd += Math.pow(s_gy-t_gy , 2); // distance between Gy in [0,255^2]
				}

				// add pixel distance to global patch distance
				distance += ssd;
			}
		}
		
		return (int)(DSCALE*distance/wsum);
	}
	
	// Helper for BufferedImage resize
	public static BufferedImage resize(BufferedImage input, int newwidth, int newheight) {
		BufferedImage out = new BufferedImage(newwidth, newheight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		java.awt.Image scaled = input.getScaledInstance(newwidth, newheight, java.awt.Image.SCALE_SMOOTH);
		g.drawImage(scaled, 0, 0, out.getWidth(), out.getHeight(), null);
		g.dispose();
		return out;
	}

	// return a copy of the image
	public MaskedImage copy() {
		boolean[][] newmask= new boolean[W][H];
		BufferedImage newimage = new BufferedImage(W,H, BufferedImage.TYPE_INT_RGB);
		newimage.createGraphics().drawImage(image, 0, 0, null);
		for(int y=0;y<H;y++)
			for(int x=0;x<W;x++)
				newmask[x][y] = mask[x][y];
		return new MaskedImage(newimage,newmask);
	}
	
	// return a downsampled image (factor 1/2)
	public MaskedImage downsample() {
		int newW=W/2, newH=H/2;
		
		// Binomial coefficient
		int[] kernel = {1,5,10,10,5,1}; 

		MaskedImage newimage = new MaskedImage(newW, newH);
		
		for(int y=0;y<H-1;y+=2) {
			for(int x=0;x<W-1;x+=2) {
				
				int r=0,g=0,b=0,m=0,ksum=0;
				
				for(int dy=-2;dy<=3;dy++) {
					int yk=y+dy;
					if (yk<0 || yk>=H) continue;
					int ky = kernel[2+dy];
					for(int dx=-2;dx<=3;dx++) {
						int xk = x+dx;
						if (xk<0 || xk>=W) continue;
						
						if (mask[xk][yk]) continue;
						int k = kernel[2+dx]*ky;
						r+= k*this.getSample(xk, yk, 0);
						g+= k*this.getSample(xk, yk, 1);
						b+= k*this.getSample(xk, yk, 2);
						ksum+=k;
						m++;
					}
				}
				if (ksum>0) {r/=ksum; g/=ksum; b/=ksum;}
	
				if (m!=0) {
					newimage.setSample(x/2, y/2, 0, r);
					newimage.setSample(x/2, y/2, 1, g);
					newimage.setSample(x/2, y/2, 2, b);
					newimage.setMask(x/2, y/2, false);
				} else {
					newimage.setMask(x/2, y/2, true);
				}
			}
		}
		
		return newimage;
	}
	
	// return an upscaled image
	public MaskedImage upscale(int newW,int newH) {
		MaskedImage newimage = new MaskedImage(newW, newH);
		
		for(int y=0;y<newH;y++) {
			for(int x=0;x<newW;x++) {
				
				// original pixel
				int xs = (x*W)/newW;
				int ys = (y*H)/newH;
				
				// copy to new image
				if (!mask[xs][ys]) {
					newimage.setSample(x, y, 0, this.getSample(xs, ys, 0));
					newimage.setSample(x, y, 1, this.getSample(xs, ys, 1));
					newimage.setSample(x, y, 2, this.getSample(xs, ys, 2));
					newimage.setMask(x, y, false);
				} else {
					newimage.setMask(x, y, true);
				}
			}
		}
		
		return newimage;
	}

}
