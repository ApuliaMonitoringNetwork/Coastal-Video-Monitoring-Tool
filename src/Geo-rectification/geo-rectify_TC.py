#!/usr/bin/env python
# -*- coding: latin-1 -*-

'''
Usage:
  geo-rectify_TC.py <image> <camera_parameters&GCPs.mat> [<z_level>] [<fig_dim_1>] [<fig_dim_2>] [<max_dist_camera>] [<geo-rectified image name>] [<geo-rectified worldfile name>]

Positional arguments:
    image					image to be rectified
    camera_parameters&GCPs			file containing K, dist, GCP's as UV_dist, XYZ, translation

Options:
	z_level 				elevation used for rectify [default: 0]
	fig_dim_1				size of output figure [default: 40] 
	fig_dim_2				size of output figure [default: 27] 
	max-DIST   				maximum distance from origin included in plot [default: 800]  
	geo-rectfied image-name			image-name of rectified image  [default:im + r'rect.jpg']
	geo-rectfied worldfile name		file-name of georeference [default:im + r'rect.jgw']
'''

'''
Geo-rectification utility developed for Torre Canne site cameras
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
		im = sys.argv[1]
		img=mimpg.imread(im)
	except:
		print 'Failed to load image' , im
	
	
	
	try:
		rectPar_TC1=sio.loadmat(sys.argv[2])
	except:
		print 'failed to load camera parameters and GCPs'
		sys.exit(1)
	
	transl=rectPar_TC1['translation']
	translation=tuple(transl[0])
	
	try:
		z_level = float(sys.argv[3])
		
	except:
		z_level=0
		print 'failed to load z, z_level =', z_level
	H = geo_rect_func.find_homography(rectPar_TC1['UV_dist'],rectPar_TC1['XYZ'],rectPar_TC1['K'],rectPar_TC1['dist'],z=z_level)
	
	u_dist, v_dist = geo_rect_func.get_pixel_coordinates(img)
	x_rect,y_rect = geo_rect_func.rectify_coordinates(u_dist,v_dist,H)
	
	
	#plot geo-rectified image and save it as plt. figure.
	try:
		fig_dim_1 = float(sys.argv[4])
		fig_dim_2 = float(sys.argv [5])		
		fig_dim = (fig_dim_1,fig_dim_2)
		max_dist = float(sys.argv[6])
	except:
		fig_dim = (40,27)
		max_dist = 800
		print 'failed to open size and/or distance; fig_dim =', fig_dim 
		print 'max_dist =', max_dist
	img_und=cv2.undistort(img,rectPar_TC1['K'],rectPar_TC1['dist'])
	fig, axs = geo_rect_func.plot_rectified_oneimage(x_rect, y_rect, img_und, rotation=None, translation=translation,figsize=fig_dim,max_distance=max_dist, ax=None)
	#axs.scatter(XY_Shoreline[0],XY_Shoreline[1])
	axs.axes.get_yaxis().set_visible(False)
	axs.axes.get_xaxis().set_visible(False)
	plt.axis('off')
	try:
		im_new = sys.argv[7]
	except:
		im_new = im + r'rect.jpg'
		print 'failed to open filename to saved, saved in', im_new
		
	plt.savefig(im_new,bbox_inches='tight',pad_inches=0.003)
	try:
		img_rect=mimpg.imread(im_new)
	except:
		print "Failed to load rectified image used for saving worldfile, asked for georeferencing"
	nx,ny = img_rect.shape[:2]
	xmin, ymin1, xmax1, ymax = [min(axs.get_xlim()), min(axs.get_ylim()), max(axs.get_xlim()), max(axs.get_ylim())]
	xres = (xmax1 - xmin) / float(ny+1)
	yres = -(ymax - ymin1) / float(nx+1)
	try:
		worldf = sys.argv[8]
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
	
	
	
###	return fig, axs, XY_Shoreline
