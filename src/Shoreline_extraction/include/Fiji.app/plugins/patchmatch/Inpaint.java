package patchmatch;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Inpaint using the PatchMatch Algorithm
 * 
 * | PatchMatch : A Randomized Correspondence Algorithm for Structural Image Editing
 * | by Connelly Barnes and Eli Shechtman and Adam Finkelstein and Dan B Goldman
 * | ACM Transactions on Graphics (Proc. SIGGRAPH), vol.28, aug-2009
 *
 * @author Xavier Philippeau
 * 
 */
public class Inpaint {
	
	//initial image
	MaskedImage initial;
	
	// Nearest-Neighbor Fields
	NNF nnf_SourceToTarget;
	NNF nnf_TargetToSource;
	
	// patch radius
	int radius;
	
	// Pyramid of downsampled initial images
	List<MaskedImage> pyramid;
	
	public BufferedImage inpaint(BufferedImage input, boolean[][] mask, int radius) {
		// initial image
		this.initial = new MaskedImage(input, mask);
		
		// patch radius
		this.radius = radius;

		// working copies
		MaskedImage source = initial, target = null;

		System.out.println("build pyramid of images...");
		
		// build pyramid of downscaled images
		this.pyramid = new ArrayList<MaskedImage>();
		this.pyramid.add(source);
		while(source.W>radius && source.H>radius) {
			source = source.downsample();
			this.pyramid.add(source);
		}
		int maxlevel=this.pyramid.size();
		
		// for each level of the pyramid 
		for(int level=maxlevel-1;level>=0;level--) {
			System.out.println("\n*** Processing -  Zoom 1:"+(1<<level)+" ***");

			// create Nearest-Neighbor Fields (direct and reverse)
			source = this.pyramid.get(level);
			
			System.out.println("initialize NNF...");
			if (level==maxlevel-1) {
				// at first, we use the same image for target and source
				// and use random data as initial guess
				target = source.copy();
				
				// we consider that initial the target contains no masked pixels  
				for(int y=0;y<target.H;y++)
					for(int x=0;x<target.W;x++)
						target.setMask(x,y,false);
				
				nnf_SourceToTarget = new NNF(source, target, radius);
				nnf_SourceToTarget.randomize();
				
				nnf_TargetToSource = new NNF(target, source, radius);
				nnf_TargetToSource.randomize();
				
			} else {
				// then, we use the rebuilt (upscaled) target 
				// and reuse the previous NNF as initial guess
				NNF new_nnf = new NNF(source, target, radius);
				new_nnf.initialize(nnf_SourceToTarget);
				nnf_SourceToTarget = new_nnf;
				
				NNF new_nnf_rev = new NNF(target, source, radius);
				new_nnf_rev.initialize(nnf_TargetToSource);
				nnf_TargetToSource = new_nnf_rev;				
			}
		
			// Build an upscaled target by EM-like algorithm (see "PatchMatch" - page 6)
			target = ExpectationMaximization(level);
		}

		return target.getBufferedImage();
	}
	
	// EM-Like algorithm (see "PatchMatch" - page 6)
	// Returns a double sized target image
	private MaskedImage ExpectationMaximization(int level) {
		
		int iterEM = 1+2*level;
		int iterNNF = Math.min(7,1+level);
		
		MaskedImage source = nnf_SourceToTarget.input;
		MaskedImage target = nnf_SourceToTarget.output;
		MaskedImage newtarget = null;
		
		System.out.print("EM loop (em="+iterEM+",nnf="+iterNNF+") : ");
		
		// EM Loop
		for(int emloop=1;emloop<=iterEM;emloop++) {

			System.out.print((1+iterEM-emloop)+" ");
			
			// set the new target as current target
			if (newtarget!=null) {
				nnf_SourceToTarget.output = newtarget;
				nnf_TargetToSource.input = newtarget;
				target = newtarget;
				newtarget = null;
			}
			
			// -- add constraint to the NNF

			// we force the link between unmasked patch in source/target
			for(int y=0;y<source.H;y++)
				for(int x=0;x<source.W;x++)
					if (!source.constainsMasked(x, y, radius)) {
						nnf_SourceToTarget.field[x][y][0] = x;
						nnf_SourceToTarget.field[x][y][1] = y;
						nnf_SourceToTarget.field[x][y][2] = 0;
					}
			for(int y=0;y<target.H;y++)
				for(int x=0;x<target.W;x++)
					if(!source.constainsMasked(x, y, radius)) {
						nnf_TargetToSource.field[x][y][0] = x;
						nnf_TargetToSource.field[x][y][1] = y;
						nnf_TargetToSource.field[x][y][2] = 0;
					}
			
			// -- minimize the NNF
			nnf_SourceToTarget.minimize(iterNNF);
			nnf_TargetToSource.minimize(iterNNF);
			
			// -- Now we rebuild the target using best patches from source
			
			MaskedImage newsource;
			boolean upscaled = false;
				
			// Instead of upsizing the final target, we build the last target from the next level source image 
			// So the final target is less blurry (see "Space-Time Video Completion" - page 5) 
			if (level>=1 && (emloop==iterEM)) {
				newsource = pyramid.get(level-1);
				newtarget = target.upscale(newsource.W,newsource.H);
				upscaled = true;
			} else {
				newsource = pyramid.get(level);
				newtarget = target.copy();
				upscaled = false;
			}

			// --- EXPECTATION STEP ---

			// votes for best patch from NNF Source->Target (completeness) and Target->Source (coherence) 
			double[][][] vote = new double[newtarget.W][newtarget.H][4];
			ExpectationStep(nnf_SourceToTarget, true, vote, newsource, upscaled);
			ExpectationStep(nnf_TargetToSource, false, vote, newsource, upscaled);

			// --- MAXIMIZATION STEP ---

			// compile votes and update pixel values
			MaximizationStep(newtarget, vote);

			// debug : display intermediary result
			BufferedImage result = MaskedImage.resize(newtarget.getBufferedImage(), initial.W, initial.H);
			Demo.display(result);
		}
		System.out.println();
		
		return newtarget;
	}

	
	// Expectation Step : vote for best estimations of each pixel
	private void ExpectationStep(NNF nnf, boolean sourceToTarget, double[][][] vote, MaskedImage source, boolean upscale) {
		int[][][] field = nnf.getField();
		int R = nnf.S;
		for(int y=0;y<nnf.input.H;y++) {
			for(int x=0;x<nnf.input.W;x++) {
				// x,y = center pixel of patch in input 
					
				// xp,yp = center pixel of best corresponding patch in output
				int xp=field[x][y][0], yp=field[x][y][1], dp=field[x][y][2];
				
				// similarity measure between the two patches
				double w = MaskedImage.similarity[dp];
				
				// vote for each pixel inside the input patch
				for(int dy=-R;dy<=R;dy++) {
					for(int dx=-R;dx<=R;dx++) {

						// get corresponding pixel in output patch
						int xs,ys,xt,yt;
						if (sourceToTarget) 
							{ xs=x+dx; ys=y+dy;	xt=xp+dx; yt=yp+dy;	}
						else
							{ xs=xp+dx; ys=yp+dy; xt=x+dx; yt=y+dy; }
						
						if (xs<0 || xs>=nnf.input.W) continue;
						if (ys<0 || ys>=nnf.input.H) continue;
						if (xt<0 || xt>=nnf.output.W) continue;
						if (yt<0 || yt>=nnf.output.H) continue;
						
						// add vote for the value
						if (upscale) {
							weightedCopy(source, 2*xs,   2*ys,   vote, 2*xt,   2*yt,   w);
							weightedCopy(source, 2*xs+1, 2*ys,   vote, 2*xt+1, 2*yt,   w);
							weightedCopy(source, 2*xs,   2*ys+1, vote, 2*xt,   2*yt+1, w);
							weightedCopy(source, 2*xs+1, 2*ys+1, vote, 2*xt+1, 2*yt+1, w);
						} else {
							weightedCopy(source, xs, ys, vote, xt, yt, w);
						}
					}
				}
			}
		}
	}

	private void weightedCopy(MaskedImage src, int xs, int ys, double[][][] vote, int xd,int yd, double w) {
		if (src.isMasked(xs, ys)) return;
		
		vote[xd][yd][0] += w*src.getSample(xs, ys, 0);
		vote[xd][yd][1] += w*src.getSample(xs, ys, 1);
		vote[xd][yd][2] += w*src.getSample(xs, ys, 2);
		vote[xd][yd][3] += w;
	}

	
	// Maximization Step : Maximum likelihood of target pixel
	private void MaximizationStep(MaskedImage target, double[][][] vote) {
		for(int y=0;y<target.H;y++) {
			for(int x=0;x<target.W;x++) {
				if (vote[x][y][3]>0) {
					int r = (int) ( vote[x][y][0]/vote[x][y][3] );
					int g = (int) ( vote[x][y][1]/vote[x][y][3] );
					int b = (int) ( vote[x][y][2]/vote[x][y][3] );
					
					target.setSample(x, y, 0, r );
					target.setSample(x, y, 1, g );
					target.setSample(x, y, 2, b );
					target.setMask(x,y,false);
				} else {
					// conserve the values from previous target
					//target.setMask(x,y,true);
				}
			}
		}
	}

}
