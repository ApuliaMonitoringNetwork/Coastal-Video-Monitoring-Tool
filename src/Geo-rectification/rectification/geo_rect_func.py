#!/usr/bin/env python
'''
Function used for rectification 

Partly used openearth/flamingo: Python toolbox for coastalimage analysis 
('An automated method for semantic classification of regions in coastal image' Hoonhoutetal. 2015)
OpenCV, Scipy, Numpy, Matplotlib libraries

'''

import cv2
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.image as mimpg
import matplotlib.cm as cm
import matplotlib.collections
import matplotlib.patches
import scipy.io as sio
import cStringIO


def find_homography(UV, XYZ, K, distortion=np.zeros((1,4)), z=0):
    '''Find homography based on ground control points

    Parameters
    ----------
    UV : np.ndarray
        Nx2 array of image coordinates of gcp's
    XYZ : np.ndarray
        Nx3 array of real-world coordinates of gcp's
    K : np.ndarray
        3x3 array containing camera matrix
    distortion : np.ndarray, optional
        1xP array with distortion coefficients with P = 4, 5 or 8
    z : float, optional
        Real-world elevation on which the image should be projected

    Returns
    -------
    np.ndarray
        3x3 homography matrix

    Notes
    -----
    Function uses the OpenCV image rectification workflow as described in
    http://docs.opencv.org/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html
    starting with solvePnP.
    '''

    UV = np.asarray(UV).astype(np.float32)
    XYZ = np.asarray(XYZ).astype(np.float32)
    K = np.asarray(K).astype(np.float32)
    
    # compute camera pose
#    rvec, tvec = cv2.solvePnP(XYZ, UV, K, distortion)[1:]
    rvec, tvec = cv2.solvePnP(XYZ, UV, K, distortion)[1:]
    
    # convert rotation vector to rotation matrix
    R = cv2.Rodrigues(rvec)[0]
    
    # assume height of projection plane
    R[:,2] = R[:,2] * z

    # add translation vector
    R[:,2] = R[:,2] + tvec.flatten()

    # compute homography
    H = np.linalg.inv(np.dot(K, R))

    # normalize homography
    H = H / H[-1,-1]

    return H
	
def get_pixel_coordinates(img):
    '''Get pixel coordinates given an image

    Parameters
    ----------
    img : np.ndarray
        NxMx1 or NxMx3 image matrix

    Returns
    -------
    np.ndarray
        NxM matrix containing u-coordinates
    np.ndarray
        NxM matrix containing v-coordinates
    '''

    # get pixel coordinates
    U, V = np.meshgrid(range(img.shape[1]),
                       range(img.shape[0]))

    return U, V


def rectify_image(img, H):
    '''Get projection of image pixels in real-world coordinates
       given an image and homography

    Parameters
    ----------
    img : np.ndarray
        NxMx1 or NxMx3 image matrix
    H : np.ndarray
        3x3 homography matrix

    Returns
    -------
    np.ndarray
        NxM matrix containing real-world x-coordinates
    np.ndarray
        NxM matrix containing real-world y-coordinates
    '''

    U, V = get_pixel_coordinates(img)
    X, Y = rectify_coordinates(U, V, H)

    return X, Y


def rectify_coordinates(U, V, H):
    '''Get projection of image pixels in real-world coordinates
       given image coordinate matrices and  homography

    Parameters
    ----------
    U : np.ndarray
        NxM matrix containing u-coordinates
    V : np.ndarray
        NxM matrix containing v-coordinates
    H : np.ndarray
        3x3 homography matrix

    Returns
    -------
    np.ndarray
        NxM matrix containing real-world x-coordinates
    np.ndarray
        NxM matrix containing real-world y-coordinates
    '''

    UV = np.vstack((U.flatten(),
                    V.flatten())).T

    # transform image using homography
    XY = cv2.perspectiveTransform(np.asarray([UV]).astype(np.float32), H)[0]
    
    # reshape pixel coordinates back to image size
    X = XY[:,0].reshape(U.shape[:2])
    Y = XY[:,1].reshape(V.shape[:2])

    return X, Y

def plot_rectified(X, Y, imgs,
                   rotation=None, translation=None, max_distance=1e4,
                   ax=None, figsize=(30,20), cmap='Greys',
                   color=True, n_alpha=0):
    '''Plot the projection of multiple RGB images in a single axis.
    Plot a list of images using corresponding lists of real-world
    x and y coordinate matrices. The resulting composition can be
    rotated and translated seperately.
    Points projected at infinite distance can be ignored by
    specifying a maximum distance.
    Parameters
    ----------
    X : list of np.ndarrays
        List of NxM matrix containing real-world x-coordinates
    Y : list of np.ndarrays
        List of NxM matrix containing real-world y-coordinates
    imgs : list of np.ndarrays
        List of NxMx1 or NxMx3 image matrices
    rotation : float, optional
        Rotation angle in degrees
    translation : list or tuple, optional
        2-tuple or list with x and y translation distances
    max_distance : float, optional
        Maximum distance from origin to be included in the plot.
        Larger numbers are considered to be beyond the horizon.
    ax : matplotlib.axes.AxesSubplot, optional
        Axis object used for plotting
    figsize : tuple, optional
        2-tuple or list containing figure dimensions
    color : bool, optional
        Whether color image should be plotted or grayscale
    n_alpha : int
        Number of border pixels to use to increase alpha
    Returns
    -------
    matplotlib.figure.Figure
        Figure object containing axis object
    matplotlib.axes.AxesSubplot
        Axis object containing plot
    '''

    # create figure
    if ax is None:
        fig, ax = plt.subplots(figsize=figsize)
    else:
        fig = ax.figure

    # loop over images
    for x, y, img in zip(X, Y, imgs):

        # find horizon based on maximum distance
        o = find_horizon_offset(x, y, max_distance=max_distance)

        # rotate to world coordinate system
        x, y = rotate_translate(x, y, rotation=rotation, translation=translation)

        # plot
        im = ax.pcolormesh(x[o:,:], y[o:,:], np.mean(img[o:,...], -1), cmap=cmap)

        # add colors
        if color:
            rgba = _construct_rgba_vector(img[o:,...], n_alpha=n_alpha)
            im.set_array(None) # remove the array
            im.set_edgecolor('none')
            im.set_facecolor(rgba)

    ax.set_aspect('equal')

    return fig, ax



def plot_rectified_oneimage(x, y, img,
                   rotation=None, translation=None, max_distance=1e4,
                   ax=None, figsize=(30,20), cmap='Greys',
                   color=True, n_alpha=0):
    '''Plot the projection of a RGB image in a single axis.

    Plot a list of images using corresponding lists of real-world
    x and y coordinate matrices. The resulting composition can be
    rotated and translated seperately.

    Points projected at infinite distance can be ignored by
    specifying a maximum distance.

    Parameters
    ----------
    X : list of np.ndarrays
        List of NxM matrix containing real-world x-coordinates
    Y : list of np.ndarrays
        List of NxM matrix containing real-world y-coordinates
    imgs : list of np.ndarrays
        List of NxMx1 or NxMx3 image matrices
    rotation : float, optional
        Rotation angle in degrees
    translation : list or tuple, optional
        2-tuple or list with x and y translation distances
    max_distance : float, optional
        Maximum distance from origin to be included in the plot.
        Larger numbers are considered to be beyond the horizon.
    ax : matplotlib.axes.AxesSubplot, optional
        Axis object used for plotting
    figsize : tuple, optional
        2-tuple or list containing figure dimensions
    color : bool, optional
        Whether color image should be plotted or grayscale
    n_alpha : int
        Number of border pixels to use to increase alpha

    Returns
    -------
    matplotlib.figure.Figure
        Figure object containing axis object
    matplotlib.axes.AxesSubplot
        Axis object containing plot
    '''

    # create figure
    if ax is None:
        fig, ax = plt.subplots(figsize=figsize)
    else:
        fig = ax.figure

    # loop over images


    # find horizon based on maximum distance
    o = find_horizon_offset(x, y, max_distance=max_distance)

    # rotate to world coordinate system
    x, y = rotate_translate(x, y, rotation=rotation, translation=translation)

    # plot
    im = ax.pcolormesh(x[o:,:], y[o:,:], np.mean(img[o:,...], -1), cmap=cmap)

    # add colors
    if color:
        rgba = _construct_rgba_vector(img[o:,...], n_alpha=n_alpha)
        im.set_array(None) # remove the array
        im.set_edgecolor('none')
        im.set_facecolor(rgba)

    ax.set_aspect('equal')

    return fig, ax


def rotate_translate(x, y, rotation=None, translation=None):
    '''Rotate and/or translate coordinate system

    Parameters
    ----------
    x : np.ndarray
        NxM matrix containing x-coordinates
    y : np.ndarray
        NxM matrix containing y-coordinates
    rotation : float, optional
        Rotation angle in degrees
    translation : list or tuple, optional
        2-tuple or list with x and y translation distances

    Returns
    -------
    np.ndarrays
        NxM matrix containing rotated/translated x-coordinates
    np.ndarrays
        NxM matrix containing rotated/translated y-coordinates
    '''
    
    if rotation is not None:
        shp = x.shape
        rotation = rotation / 180 * np.pi

        R = np.array([[ np.cos(rotation),np.sin(rotation)],
                      [-np.sin(rotation),np.cos(rotation)]])

        xy = np.dot(np.hstack((x.reshape((-1,1)),
                               y.reshape((-1,1)))), R)
    
        x = xy[:,0].reshape(shp)
        y = xy[:,1].reshape(shp)

    if translation is not None:
        x += translation[0]
        y += translation[1]
    
    return x, y


def find_horizon_offset(x, y, max_distance=1e4):
    '''Find minimum number of pixels to crop to guarantee all pixels are within specified distance

    Parameters
    ----------
    x : np.ndarray
        NxM matrix containing real-world x-coordinates
    y : np.ndarray
        NxM matrix containing real-world y-coordinates
    max_distance : float, optional
        Maximum distance from origin to be included in the plot.
        Larger numbers are considered to be beyond the horizon.

    Returns
    -------
    float
        Minimum crop distance in pixels (from the top of the image)
    '''

    offset = 0
    if max_distance is not None:
        try:
            th = (np.abs(x)>max_distance)|(np.abs(y)>max_distance)
            offset = np.max(np.where(np.any(th, axis=1))) + 1
        except:
            pass

    return offset


def _construct_rgba_vector(img, n_alpha=0):
    '''Construct RGBA vector to be used to color faces of pcolormesh

    Parameters
    ----------
    img : np.ndarray
        NxMx3 RGB image matrix
    n_alpha : int
        Number of border pixels to use to increase alpha

    Returns
    -------
    np.ndarray
        (N*M)x4 RGBA image vector
    '''

    alpha = np.ones(img.shape[:2])    
    
    if n_alpha > 0:
        for i, a in enumerate(np.linspace(0, 1, n_alpha)):
            alpha[:,[i,-2-i]] = a
        
    rgb = img[:,:-1,:].reshape((-1,3)) # we have 1 less faces than grid cells
    rgba = np.concatenate((rgb, alpha[:,:-1].reshape((-1, 1))), axis=1)

    if np.any(img > 1):
        rgba[:,:3] /= 255.0
    
    return rgba


