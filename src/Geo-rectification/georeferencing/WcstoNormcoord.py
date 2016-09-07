#!/usr/bin/env python
'''
Usage:
  WcstoNormcoord.py <image> <coord_shrl.json> <jgw file> [<normalize_shoreline_cooridnates>]
  
Positional arguments:
	image								*jpg image for shape information
	geo-rectied shp shoreline			*json files name conteining the shoreline real wolrd coordinates
	scale information					*.jgw file

Options:
	normalize_shoreline_cooridnates			json file name where to saved the normalizecoordiates of shoreline
'''

'''
Python utilities in order to normlaize coordinates for plotting on web-tool
es. python WcstoNormcoord.py image.jpg shorl.shp image.jgw
'''

import matplotlib
matplotlib.use('Agg')
import matplotlib.image as mimpg
import numpy as np
import json

if __name__ == '__main__':
	import sys
	try:
		im = (sys.argv[1])
		coord_shrl_json = (sys.argv[2])
		jgwfile = (sys.argv[3])
	except:
		sys.exit(1)
		
		shrl_sjp = (sys.argv[1])
	jgw = [line.rstrip('\n') for line in open(jgwfile)]
	img=mimpg.imread(im)
	with open(coord_shrl_json) as data:
		data1=json.load(data)
	shrl=np.asarray(data1.values())
	
#coord_wcs=point_store.load(coord_shrl_shp)
	
#transform string to floating and unzip/array shorl
	ymax=float(jgw[5])
	xmin=float(jgw[4])
	xmax=(xmin)+(float(jgw[0])*img.shape[1])
	ymin=(ymax)+(float(jgw[3])*img.shape[0])
	x2, y2 = zip(*shrl[0])
	x = np.asarray(x2)
	y = np.asarray(y2)
	#normalize coordinates
	x_norm= (1-(-1))*(x-xmin)/(xmax-xmin) + -1;
	y_norm= (1-(-1))*(y-ymin)/(ymax-ymin) + -1;
	data=zip(x_norm,y_norm)
		#save coord
	
	try:
		jsonfile = (sys.argv[4])
	except:
		jsonfile = str(coord_shrl_json + '.json')
		print 'File shoreline shp saved to', jsonfile
	with open(jsonfile, 'wb') as outfile:
		json.dump({"shoreline": data}, outfile)
		
		
