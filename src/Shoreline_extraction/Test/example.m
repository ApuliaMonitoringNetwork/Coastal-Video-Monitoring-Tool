%% Matlab call:

% example in order to extract shoreline from image using seeds.
% this main function is ready to use for Alimini database images. 

addpath(genpath('../../Shoreline_extraction'));

Shoreline_extraction_color_Al('../Test/2006.12.26_15.01.09MediaTc0.jpg','seeds_shorel_ex.mat','../include/Fiji.app','shoreline');
