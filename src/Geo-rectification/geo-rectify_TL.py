#!/usr/bin/env python
'''
Usage:
  geo-rectify_TL.py <image_TL1> <image_TL2> <camera_parameters&GCPs_TL1.mat> <camera_parameters&GCPs_TL2.mat> [<z_level>] [<fig_dim_1>] [<fig_dim_2>] [<max_dist_camera_TL1>] [<max_dist_camera_TL2>]  [<geo-rectified image name>] [<geo-rectified worldfile name>]
  
Positional arguments:
	image_TL1					image to be rectified_TL1
	image_TL2					image to be rectified_TL2
	camera_parameters&GCPs_TL1			file containing K, dist, GCP's as UV_dist, XYZ, translation
	camera_parameters&GCPs_TL2			file containing K, dist, GCP's as UV_dist, XYZ, translation
	
Options:
	z_level						elevation used for rectify
	fig_dim_1					size of output figure	[default: 80] 
	fig_dim_2					size of output figure	[default: 53] 
	max_dist_camera_TL1				maximum distance from origin included in plot	[default: 800]  
	max_dist_camera_TL2				maximum distance from origin included in plot	[default: 800]  
	geo-rectified image-name			image-name of rectified image	[default:image_TL1 + r'rect.jpeg']
	geo-rectified worldfile name			file-name of georeference	[default:image_TL1 [:-3] + r'jgw']
'''

'''
Geo-rectification utility developed for Torre Lapillo (PortCeszareo) site cameras
'''

#from common import Sketcher

import cv2
import numpy as np
import scipy.io as sio
import point_store
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import geo_rect_func
import matplotlib.image as mimpg


if __name__ == '__main__':
	import sys
	try:
		im_TL1 = sys.argv[1]
		im_TL2 = sys.argv[2]
		img_TL1=mimpg.imread(im_TL1)
		img_TL2=mimpg.imread(im_TL2)
	except:
		print 'Failed to load image' , im
	
	
	
	try:
		rectPar_TL1=sio.loadmat(sys.argv[3])
		rectPar_TL2=sio.loadmat(sys.argv[4])
	except:
		print 'failed to load camera parameters and GCPs'
		sys.exit(1)
	
	transl=rectPar_TL1['translation']
	translation=tuple(transl[0])
	
	try:
		z_level = float(sys.argv[5])
		
	except:
		z_level=0
		print 'failed to load z, z_level =', z_level
	H_TL1 = geo_rect_func.find_homography(rectPar_TL1['UV_dist'],rectPar_TL1['XYZ'],rectPar_TL1['K'],rectPar_TL1['dist'],z=z_level)
	H_TL2 = geo_rect_func.find_homography(rectPar_TL2['UV_dist'],rectPar_TL2['XYZ'],rectPar_TL2['K'],rectPar_TL2['dist'],z=z_level)
	
	u_dist_TL1, v_dist_TL1 = geo_rect_func.get_pixel_coordinates(img_TL1)
	x_rect_TL1, y_rect_TL1 = geo_rect_func.rectify_coordinates(u_dist_TL1,v_dist_TL1,H_TL1)
	u_dist_TL2, v_dist_TL2 = geo_rect_func.get_pixel_coordinates(img_TL2)
	x_rect_TL2, y_rect_TL2 = geo_rect_func.rectify_coordinates(u_dist_TL2,v_dist_TL2,H_TL2)
		
	#plot geo-rectified image and save it as plt. figure.
	try:
		fig_dim_1 = float(sys.argv[6])
		fig_dim_2 = float(sys.argv [7])		
		fig_dim = (fig_dim_1,fig_dim_2)
		max_dist_TL1 = float(sys.argv[8])
		max_dist_TL2 = float(sys.argv[9])
	except:
		fig_dim = (80,53)
		max_dist_TL1 = 800
		max_dist_TL2 = 800
		print 'failed to open size and/or distance; fig_dim =', fig_dim 
		print 'max_dist_TL1 =', max_dist_TL1
		print 'max_dist_TL2 =', max_dist_TL2
	fig, axs_TL1 = geo_rect_func.plot_rectified_oneimage(x_rect_TL1, y_rect_TL1, cv2.undistort(img_TL1,rectPar_TL1['K'],rectPar_TL1['dist']), rotation=None, translation=translation,figsize=fig_dim, max_distance=max_dist_TL1, ax=None)
	fig_2, axs = geo_rect_func.plot_rectified_oneimage(x_rect_TL2, y_rect_TL2, cv2.undistort(img_TL2,rectPar_TL2['K'],rectPar_TL2['dist']), rotation=None, translation=translation, figsize=fig_dim, max_distance=max_dist_TL2, ax=axs_TL1)
	axs.axes.get_yaxis().set_visible(False)
	axs.axes.get_xaxis().set_visible(False)
	plt.axis('off')
	#axs.scatter(XY_Shoreline[0],XY_Shoreline[1])
	try:
		im_new = sys.argv[10]
	except:
		im_new = im_TL1 + r'rect.jpeg'
		print 'failed to open filename to saved, saved in', im_new
		
	plt.savefig(im_new,bbox_inches='tight',pad_inches=0.003)
	try:
		img_rect=mimpg.imread(im_new)
	except:
		print "Failed to load rectified image used for saving worldfile, ask for georeferencing"
	#select size image
	nx,ny = img_rect.shape[:2]
	#axis limits
	xmin, ymin1, xmax1, ymax = [min(axs.get_xlim()), min(axs.get_ylim()), max(axs.get_xlim()), max(axs.get_ylim())]
	#image m/pixel resolution (ymin1 and xmax1 are not real value on the image, but xres, yres consider one more pixel line on right and bottom)
	xres = (xmax1 - xmin) / float(ny+1)
	yres = -(ymax - ymin1) / float(nx+1)
	try:
		worldf = sys.argv[11]
	except:
		worldf = im_new[:-3] + r'jgw'
	worldfile = open(worldf, "w")   
	worldfile.write(str(xres)+"\n")
	worldfile.write(str(0)+"\n") # = 0
	worldfile.write(str(0)+"\n") # = 0
	worldfile.write(str(yres)+"\n")
	worldfile.write(str(xmin)+"\n")
	worldfile.write(str(ymax)+"\n")
	worldfile.close()
	
	
#return *.jgw worlfile name
