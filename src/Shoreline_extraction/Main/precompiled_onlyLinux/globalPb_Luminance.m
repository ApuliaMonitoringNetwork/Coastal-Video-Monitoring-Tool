function [gPb_orient, gPb_thin, textons] = globalPb_Luminance(imgFile, outFile, rsz)
% syntax:
%   [gPb_orient, gPb_thin, textons] = globalPb(imgFile, outFile, rsz)
%
% description:
%   compute Globalized Probability of Boundary of an image.
%
% arguments:
%   imgFile : image file
%   outFile:  mat format (optional)
%   rsz:      resizing factor in (0,1], to speed-up eigenvector computation
%
% outputs (uint8):
%   gPb_orient: oriented lobalized probability of boundary.
%   gPb_thin:  thinned contour image.
%   textons
%
% Nico Valentini including work of Pablo Arbelaez <arbelaez@eecs.berkeley.edu> 
% December 2010
if nargin<3, rsz = 1.0; end
if nargin<2, outFile = ''; end

if ((rsz<=0) || (rsz>1)),
    error('resizing factor rsz out of range (0,1]');
end

im = double(imread(imgFile)) / 255;
[tx, ty, nchan] = size(im);
orig_sz = [tx, ty];

% default feature weights
if nchan == 3,
    % work good_ weights = [0.0007  0.0008  0.0039  0.0020  0.0028  0.0029  0.0060  0.0064  0.0079  0.0024  0.0027  0.0170  0.0074];
    
        
        %weights = [0.02 0.03 0.042 0.0040 0.00380 0.0029 0.07 0.064 0.079  0.0010 0.0007 0.0241 0.0034];
%weight upgraded_ServerMultitel
        weights = [0.0059  0.0048  0.002  0.00020  0.00022  0.00030  0.0016  0.0014  0.0010  0.00054  0.000727  0.000840  0.0072];

else
    weights = [ 0   0    0.0054         0         0         0         0         0         0    0.0048    0.0049    0.0264    0.0090];
end

%% mPb
[mPb, mPb_rsz, bg1, bg2, bg3, cga1, cga2, cga3, cgb1, cgb2, cgb3, tg1, tg2, tg3, textons] = multiscalePb_Luminance(im, rsz);

%% sPb
outFile2 = strcat(outFile, '_pbs.mat');
[sPb] = spectralPb(mPb_rsz, orig_sz, outFile2);
delete(outFile2);

%% gPb
gPb_orient = zeros(size(tg1));
for o = 1 : size(gPb_orient, 3),
    l1 = weights(1)*bg1(:, :, o);
    l2 = weights(2)*bg2(:, :, o);
    l3 = weights(3)*bg3(:, :, o);

    a1 = weights(4)*cga1(:, :, o);
    a2 = weights(5)*cga2(:, :, o);
    a3 = weights(6)*cga3(:, :, o);

    b1 = weights(7)*cgb1(:, :, o);
    b2 = weights(8)*cgb2(:, :, o);
    b3 = weights(9)*cgb3(:, :, o);

    t1 = weights(10)*tg1(:, :, o);
    t2 = weights(11)*tg2(:, :, o);
    t3 = weights(12)*tg3(:, :, o);

    sc = weights(13)*sPb(:, :, o);

    gPb_orient(:, :, o) = l1 + a1 + b1 + t1 + l2 + a2 + b2 + t2 + l3 + a3 + b3 + t3 + sc;
end

%% outputs
gPb = max(gPb_orient, [], 3);

gPb_thin = gPb .* (mPb>0.05);
gPb_thin = gPb_thin .* bwmorph(gPb_thin, 'skel', inf);

if ~strcmp(outFile,''), save(outFile,'gPb_thin', 'gPb_orient','textons'); end

