#!/usr/bin/env python
'''
Usage:
  geo-rectify-shoreline_shp&json.py <ImageCoord_shoreline.mat> <camera_parameters&GCPs.mat> [<geo-rectified shp shoreline>] [<geo-rectified json shoreline>] [<z-level>] 
  
Positional arguments:
	Shoreline_Image_Coordinates			mat files name conteining the shoreline image coordinates
	camera_parameters&GCPs				file containing K, dist, GCP's as UV_dist, XYZ, translation (*.mat file)
	
Options:
	geo-rectied shp shoreline			file name where to saved the shp file
	geo-rectified json shoreline		file name where to saved the json file
	z-level								reference level where the shoreline is rectified
'''

'''
es. python geo-rectify-shoreline_shp&json.py shorelineUV.mat rectPar_TC1.mat shorelineXY.shp 0
'''

import cv2
import point_store
import scipy.io as sio
import numpy as np
import geo_rect_func
import shapefile
from json import dumps
import json

if __name__ == '__main__':
	import sys
	try:
		shl = (sys.argv[1])
		UVshorl_ori=sio.loadmat(shl)
		rectPar_TC1=sio.loadmat(sys.argv[2])
		transl=rectPar_TC1['translation']
		translation=tuple(transl[0])
		try:
			z_level = float(sys.argv[5])
		except:
			z_level = 0
			print 'Failed to load z_level, using z=0'
		H = geo_rect_func.find_homography(rectPar_TC1['UV_dist'],rectPar_TC1['XYZ'],rectPar_TC1['K'],rectPar_TC1['dist'],z=z_level)
		#matlab to python coordinates system
		UVshorl_ori['shorlUV']=UVshorl_ori['shorlUV']-1
		#undistort points
		UVshorl_resh=np.asarray(np.reshape(UVshorl_ori['shorlUV'],(UVshorl_ori['shorlUV'].shape[0],1,2)),dtype='float64')
		UVshorl_und=cv2.undistortPoints(UVshorl_resh,rectPar_TC1['K'],rectPar_TC1['dist'])
		undistorted=np.concatenate((UVshorl_und[:,0,:1]* rectPar_TC1['K'][0][0] + rectPar_TC1['K'][0][2] ,UVshorl_und[:,0,1:]* rectPar_TC1['K'][1][1] + rectPar_TC1['K'][1][2]),axis=1)
		#perspectiveTransform
		XY_shor_und = cv2.perspectiveTransform(np.asarray(undistorted.reshape(undistorted.shape[0],1,2),dtype='float64'), H)
		#rotating_translating to world coordinate system
		XY_Shoreline = geo_rect_func.rotate_translate(np.asarray(XY_shor_und[:,0,0],dtype='float64'),np.asarray(XY_shor_und[:,0,1],dtype='float64'),rotation=None,translation=tuple(translation))
		
		#saving real-world coordinates of shoreline 
		xy_srl=zip(list(XY_Shoreline[0]),list(XY_Shoreline[1]))
		try:
			shrl_name = sys.argv[3]
		except: 
			shrl_name = str(shl + '.shp')
			print 'File shoreline shp saved to', shrl_name
		#point_store.save(shrl_name,xy_srl,'+proj=utm +zone=33 +ellps=WGS84 +datum=WGS84 +units=m +no_defs ')
		point_store.save(shrl_name,xy_srl,'+proj=utm +zone=33 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs ')
		reader = shapefile.Reader(shrl_name)
		fields = reader.fields[1:]
		field_names = [field[0] for field in fields]
		buffer = []
		for sr in reader.shapeRecords():
			atr = dict(zip(field_names, sr.record))
			geom = sr.shape.__geo_interface__
			asa=geom.values()
			buffer.append(asa[-1])
			
		try:
			json_shrl_name = sys.argv[4]
		except: 
			json_shrl_name = str(shrl_name + '.json')
			print 'File shoreline shp saved to', json_shrl_name
		geoj = open(json_shrl_name,'w')
		geoj.write(dumps({"wcsShoreline":buffer}))
		geoj.close()
		
	except:
		print 'Failed to load file:', shl
		sys.exit(1)