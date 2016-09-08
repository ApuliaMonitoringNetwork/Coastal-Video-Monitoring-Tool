##Tool
This toolbox is is mainly based on a new algorithm, implementing image-processing procedures, which allow extracting the sea/land boundary from automatic segmented Coastal Timex images.

#Abstract

A pre-processing routine has been developed in order to bypass issues due to low/different illumination, contrast and the presence of objects/algae on the swash area, greatly spread in the Apulia region (IT). A distribution of the popular software ImageJ, the Fiji software, is used for this task. Then, the main shoreline detection algorithm is implemented, inspired by the Global Probability of Boundary detector, combined with segmentation steps based on detected boundaries procedures and, then, on the color properties recognition for intertidal bars detection and a parallel overall correction. In the present work the ROI area limits, widely adopted worldwide, are eliminated, in order to face regional morphological/urbanization properties due the combination of both swash length seasonal differences and low cameras height. While small seeds pixel areas are introduced in order to cope mainly with the constrained segmentation on the Ultrametric Contour Map.

Notes
This work and codes are inspired by the work "Contour Detection and Hierarchical Image Segmentation" by Pablo Arbeláez, Michael Maire, Charless Fowlkes, and Jitendra Malik.


