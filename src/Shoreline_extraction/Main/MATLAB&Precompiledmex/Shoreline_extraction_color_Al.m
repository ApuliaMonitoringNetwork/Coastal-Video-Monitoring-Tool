%% Shoreline extraction using GlobalProbability of Boundary, and UCM 
% (Ultrametric COntour Map) based on the max oriened gPb and Constrained
% Segmentation (see Arbelaez et al., 2011) modified.

function [seeds, obj_names, seg, cmap, seeds_ucm_img, bndry_img, data] = ...
    Shoreline_extraction_color_Al(imag, seeds, fiji_path, shorl_name)

%	image			jpg format image to be segmented and shoreline extracted;

%	seeds			1-D mask few pixels or area to be defined keeping in mind 
%                		they do have to not changed during all acquisition time,
%                		labeled by "1" for sky, "2" for sand, "3" for sea;
%                		If empty, default load('seeds_shorel_ex.mat').

%	gPb_orient 	 	the oriented values of Global Probability of Boundaries
%                		already calculated (.mat file conteining gPb_orient, 
%                		gPb_thin and textons) or to be calculated;

%	shorl_name		filename of the shoreline to saved 
%	fiji_path		path of the Fiji repos


[pat,file,ext]=fileparts(imag);
if isempty(pat)
    pat=cd
end

%read image and double it
im=im2double(imread(imag));

%check for seeds, otherwise load it
if ((nargin < 2) || (isempty(seeds)) || (ischar(seeds)))
    try load(seeds);
    catch
        seeds=load('seeds_TC.mat');
        seeds=seeds.seeds;
    end
end
%check for gPb.mat file of global Prob of boundary over the image
if ((nargin < 3)) || (isempty(fiji_path) || ischar(fiji_path))
    try 
        addpath(genpath(fiji_path));
    catch
        warning('impossibile to addpath FIJI_path');
        %add java classes for matlab interaction
    end
end
        javaaddpath([fiji_path, '/ij-1.49v.jar']);
        javaaddpath([fiji_path, '/mij.jar']);
        %javaclasspath;
        %activate in background Fiji in Matlab
        %jaraddfiles();
        Miji_exe(false,fiji_path);

        wher=fullfile(imag);
        jstr = java.lang.String(['path=[' wher ']' ]);

        %preprocecessing steps for colorMainlyBased image
        preprocess_miji_color_exe();
        
        outFile=([ pat '/' char(tit) '_gb.mat']);



%% modification for MAYOR color contrast
% histogram distance
labTransformation = makecform('srgb2lab');
im_1=imread(imag);
lab_cr = applycform(im_1,labTransformation);
b=im2double(lab_cr(:,:,3));
Lu=im2double(lab_cr(:,:,1));
sea=b(seeds==3);
sand=b(seeds==2);
sea_L=Lu(seeds==3);
sand_L=Lu(seeds==2);
hi_sand=hist((sand),256)./numel(sand);
hi_sea=hist((sea),256)./numel(sea);
hi_sand_L=hist((sand_L),256)./numel(sand_L);
hi_sea_L=hist((sea_L),256)./numel(sea_L);
f_b = sum((hi_sand' - hi_sea').^2);
f_L = sum((hi_sand_L' - hi_sea_L').^2);

% Global Prob. of Boundary
if f_L>f_b
        gPb_orient = globalPb_pieces_lum(imgFile, outFile); f_var=logical(0);
else
        gPb_orient = globalPb_pieces(imgFile, outFile); f_var=logical(1);
end

%determine the Ultrametric COntour Map from gPb_orient, following Arbelaez et al. 2011
ucm2 = contours2ucm(gPb_orient,'doubleSize');
obj_names={'sky','sand','sea'};

%% authomatic image segmentation based on 3 objects {'sea','sand','sea'}
% 3 images results
[seeds, obj_names, seg, data] = Segment_image_color_Al(im, ucm2, seeds, obj_names,f_var);


%% extracting shoreline from bndry_img

%evaluate if it needs some modifications the segmentation is based on the 
%three labels=sky, sea and sand in next future we could be able to determine 
%also some others labels, for ex. the distinction of wet adn dry sand, but 
%also the dune if it would be available....
seg(seg==1)=3;
for i = 1:max(max(seg));
    seglab(:,:,i)=(seg==i);
end
land_sea=double(seglab(:,:,2));


% tricks in order to eliminate the issue due to black left line
for i =1:size(land_sea,1)
    for k=3:6
        if land_sea(i,1:k)~=land_sea(i,k+1:2*k);
            land_sea(i,1:k)=~land_sea(i,1:k);

        else
        end
    end
end
% land_sea(1:70,:)=0;

labels=land_sea;
[sx sy] = size(labels);

% old implementation_ 

% dx  = (labels(1:end-1,:) ~= labels(2:end,:));
% dy  = (labels(:,1:end-1) ~= labels(:,2:end));
% dxy = (labels(1:end-1,1:end-1) ~= labels(2:end,2:end));
% dyx = (labels(2:end,1:end-1) ~= labels(1:end-1,2:end));
% % mark thick boundaries along each direction
% bx  = ([dx; zeros([1 sy])] | [zeros([1 sy]); dx]);
% by  = ([dy  zeros([sx 1])] | [zeros([sx 1])  dy]);
% bxy = zeros(size(labels));
% bxy(1:end-1,1:end-1) = bxy(1:end-1,1:end-1) | dxy;
% bxy(2:end,2:end)     = bxy(2:end,2:end)     | dxy;
% byx = zeros(size(labels));
% byx(2:end,1:end-1) = byx(2:end,1:end-1) | dyx;
% byx(1:end-1,2:end) = byx(1:end-1,2:end) | dyx;
% 
% % combine boundaries
% shoreline1 = bx | by | bxy | byx;

%% new implementation LINE SHORELINE
bw_bound=edge(labels,0.0001);

%% subpixel ACCURACY detection
seg1=seg;
threshold = 0.2;
iter = 2;
segi=0.6;

[edges, RI] = subpixelEdges(seg1, threshold, 'SmoothingIter', iter); 
xx=edges.x-segi/2*edges.ny;
yy= edges.y+segi/2*edges.nx;
shorlUV=[xx,yy];

% saving final Shoreline
shoreline_ent=shorlUV;

save([shorl_name '.mat'], 'shorlUV')
save([shorl_name 'ucm.mat'],'ucm2');


end
