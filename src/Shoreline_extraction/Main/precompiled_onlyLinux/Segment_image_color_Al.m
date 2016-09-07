function [seeds, obj_names, seg, cmap, seeds_ucm_img, bndry_img, data] = ...
    Segment_image_color_Al(im, ucm, seeds, obj_names, f_var,filename, fh )

% Segmentation process based on Constrained segmentation

set(0, 'DefaultFigureVisible', 'off')
set(0, 'DefaultAxesVisible', 'off')
% get image size
[sx sy sz] = size(im);

% initialize empty seeds if not specified
if ((nargin < 3) || (isempty(seeds)))
   seeds = zeros([sx sy]);
end

% get number of objects
data.image.n_objects = max(max(seeds(:)),1);

% initialize empty object names if not specified
if ((nargin < 4) || (isempty(obj_names)))
   obj_names = cell([data.image.n_objects 1]);
else
   if (length(obj_names) < data.image.n_objects)
      obj_names_old = obj_names;
      obj_names = cell([data.image.n_objects 1]);
      obj_names(1:length(obj_names_old)) = obj_names_old;
   else
      obj_names = obj_names(1:data.image.n_objects);
   end
end

% set filename to 'unknown' if not specified
if ((nargin < 6) || (isempty(filename)))
   filename = 'unknown';
end

% use default figures if not specified
if ((nargin < 7) || (isempty(fh)))
   fh = 1;
end

% set other figure handles
% fh_seg  = fh + 1; % figure for displaying segmentation results 
% fh_bdry = fh + 2; % figure for displaying segmentation boundary

% % clear figures
% figure(fh); clf;
% figure(fh_seg); clf;
% figure(fh_bdry); clf;

% compute initial segmentation
[boundary labels] = compute_uvt(ucm, seeds);

% store image segmentation data
data.image.im        = im;
data.image.ucm       = ucm;
data.image.seeds     = seeds;
data.image.labels    = labels;
data.image.boundary  = boundary;
data.image.obj_names = obj_names;

% store backup of image data
data.image_store = data.image;

% store figure data
data.figures.fh      = fh;
% data.figures.fh_seg  = fh_seg;
% data.figures.fh_bdry = fh_bdry;

% store current state 
data.state.obj_id = 1;
data.state.cmap = cmap_n(data.image.n_objects);
% data.state.final_action = 'none';

% update figures
% disp_update(data);



% return seeds, object names
seeds = data.image.seeds;
obj_names = data.image.obj_names;
seg = resize_labels(data.image.labels);
% action load = data.state.final_action;
cmap = data.state.cmap;
% seeds_ucm_img = seeds_ucm_overlay_inv(ucm, seeds);
% bndry_img = boundary_overlay(im, data.image.labels);

%% Storing variables and Validate results 
data.image_store = data.image;

for i=1:(data.image.n_objects)
    if strcmp(data.image.obj_names(i),'sea')
        seas=(data.image.labels==i);
    elseif strcmp(data.image.obj_names(i),'sand')
        sand=(data.image.labels==i);
    end
end
    
        
xs = 3:2:size(ucm,1);
ys = 3:2:size(ucm,2);
ucm = ...
    max(max(ucm(xs,ys),ucm(xs-1,ys)), ...
    max(ucm(xs,ys-1),ucm(xs-1,ys-1)));
seas=double(seas);
sand=double(sand);
seas= ...
    max(max(seas(xs,ys),seas(xs-1,ys)), ...
    max(seas(xs,ys-1),seas(xs-1,ys-1)));
sand= ...
    max(max(sand(xs,ys),sand(xs-1,ys)), ...
    max(sand(xs,ys-1),sand(xs-1,ys-1)));

[si,tx,th]=size(im);
imlab=RGB2Lab(im);
iml=imlab(:,:,1);
ima=imlab(:,:,2);
imb=imlab(:,:,3);      
imL=imNormalize(imb,5);

stats_sea=regionprops(seas,imL,'MeanIntensity','PixelValues','MaxIntensity','PixelList','centroid');
stats_sand=regionprops(sand,imL,'MeanIntensity','PixelValues','MaxIntensity','PixelList','centroid');

%"par" parameter determining number of times the ucm mean has to be consider
%for minimum binary regionalization 


% par = Threshold Parameters for UCM segmentation in the last stage 
% of subtidal bar research and Previous constrained segmentation validation. 
par=7;
ucm_bin=ucm>mean(mean(ucm))*par;
ucm_bin=~ucm_bin;
stats_ucm=regionprops(ucm_bin,imL,'MeanIntensity','PixelValues','MaxIntensity','PixelList','centroid');

j=1;
sa=1;
for k=1:numel(stats_ucm)
    xy=stats_ucm(k).PixelList;
    if length(xy)<=1/6*(si*tx)
    mask=zeros(si,tx);
    for i=1:size(xy,1)
        mask(xy(i,2),xy(i,1))=1;
        
    end
    %figure(5),imshow(mask)
    %figure(f),imshow(mask)
    if f_var
        if sum(sum(mask&seas)) > 0 && all(stats_ucm(k).Centroid - ...
            stats_sea.Centroid < [si/6,tx/6]) && ...
            sum(sum(mask&double(data.image.seeds~=(find(strcmpi(data.image.obj_names(:),'sea')))))) >= length((find(mask)))
            if abs(stats_ucm(k).MeanIntensity-std(stats_ucm(k).PixelValues) ...
                -stats_sand.MeanIntensity) < 0.3;
                if abs(stats_ucm(k).MeanIntensity ...
                - stats_sea.MeanIntensity) > 0.12; %before 0.12
                    prob_sea_out(j:j-1+length(stats_ucm(k).PixelList),:)=stats_ucm(k).PixelList;
                    j=j+length(stats_ucm(k).PixelList);
                end
            end
        end
            
        if sum(sum(mask&sand))>0 && all(stats_ucm(k).Centroid - ...
                stats_sea.Centroid < [si/6,tx/6]) && ... 
                sum(sum(mask&double(data.image.seeds~=(find(strcmpi(data.image.obj_names(:),'sand')))))) >= length((find(mask))) 
            if abs(stats_ucm(k).MeanIntensity-std(stats_ucm(k).PixelValues) ...
                    - stats_sea.MeanIntensity) < 0.06;
                if abs(stats_ucm(k).MeanIntensity ...
                    - stats_sea.MeanIntensity) > 0.15; %based 
                    prob_sand_sea(sa:sa-1+length(stats_ucm(k).PixelList),:)=stats_ucm(k).PixelList;
                    %figure(g),imshow(mask)
                    sa=sa+length(stats_ucm(k).PixelList);
                end
            end
        end
    else
        if sum(sum(mask&seas)) > 0 && all(stats_ucm(k).Centroid - ...
            stats_sea.Centroid < [si/6,tx/6]) && ...
            sum(sum(mask&double(data.image.seeds~=(find(strcmpi(data.image.obj_names(:),'sea')))))) >= length((find(mask)))
            if abs(stats_ucm(k).MeanIntensity-std(stats_ucm(k).PixelValues) ...
                -stats_sand.MeanIntensity) < 0.2;
                if abs(stats_ucm(k).MeanIntensity ...
            - stats_sea.MeanIntensity) > 0.12; %before 0.12
                    prob_sea_out(j:j-1+length(stats_ucm(k).PixelList),:)=stats_ucm(k).PixelList;
                    j=j+length(stats_ucm(k).PixelList);
                end
            end
        end
            
        if sum(sum(mask&sand))>0 && all(stats_ucm(k).Centroid - ...
                stats_sea.Centroid < [si/6,tx/6]) && ... 
                sum(sum(mask&double(data.image.seeds~=(find(strcmpi(data.image.obj_names(:),'sand')))))) >= length((find(mask))) 
            if abs(stats_ucm(k).MeanIntensity-std(stats_ucm(k).PixelValues) ...
                    - stats_sea.MeanIntensity) < 0.05;
                if abs(stats_ucm(k).MeanIntensity ...
                - stats_sea.MeanIntensity) > 0.15; %based 
                    prob_sand_sea(sa:sa-1+length(stats_ucm(k).PixelList),:)=stats_ucm(k).PixelList;
                    %figure(g),imshow(mask)
                    sa=sa+length(stats_ucm(k).PixelList);
                end
            end
        end
        
    end
end
end

if exist('prob_sea_out','var')
x=prob_sea_out(1:end,1);y=prob_sea_out(1:end,2);
% 2 equal to sand
for lenx=1:length(x)
data.image.seeds(y(lenx),x(lenx)) = 2;
data.image.prob_sea_out=prob_sea_out;
end
end
if exist('prob_sand_sea','var')
z=prob_sand_sea(1:end,1);w=prob_sand_sea(1:end,2);
% 2 equal to sand
for lenx=1:length(z)
data.image.seeds(w(lenx),z(lenx)) = 3;
data.image.prob_sand_sea=prob_sand_sea;
end
end
%disp_ui_status(data, 'Computing segmentation ...');
[boundary labels] = compute_uvt(data.image.ucm, data.image.seeds);
data.image.boundary = boundary;
data.image.labels = labels;
seg = resize_labels(data.image.labels);

%% Helper Functions


% compute constrained segmentation
function [boundary labels] = compute_uvt(ucm, seeds)
      % determine relative ucm/seeds scale
      if (size(ucm,1) >= 4.*size(seeds,1))
         step = 4;
      else
         step = 2;
      end
      % double seeds size
      seeds_lg = zeros(size(ucm));
      seeds_lg(2:step:end,2:step:end) = seeds;
      % compute segmentation
      [boundary labels] = uvt(ucm, seeds_lg);
      % resize labels 
      if (size(ucm,1) >= 4.*size(seeds,1))
         labels = labels(2:2:end,2:2:end);
      end
   end

   %% resize segmentation labels to original image size
   function labels_sm = resize_labels(labels)
      labels_sm = labels(2:2:end,2:2:end);
   end

   %% compute (thick) boundary from labels
   function boundary = boundary_from_labels(labels)
      [sx sy] = size(labels);
      % compute vertical, horizontal, diagonal differences
      dx  = (labels(1:end-1,:) ~= labels(2:end,:));
      dy  = (labels(:,1:end-1) ~= labels(:,2:end));
      dxy = (labels(1:end-1,1:end-1) ~= labels(2:end,2:end));
      dyx = (labels(2:end,1:end-1) ~= labels(1:end-1,2:end));
      % mark thick boundaries along each direction
      bx  = ([dx; zeros([1 sy])] | [zeros([1 sy]); dx]);
      by  = ([dy  zeros([sx 1])] | [zeros([sx 1])  dy]);
      bxy = zeros(size(labels));
      bxy(1:end-1,1:end-1) = bxy(1:end-1,1:end-1) | dxy;
      bxy(2:end,2:end)     = bxy(2:end,2:end)     | dxy;
      byx = zeros(size(labels));
      byx(2:end,1:end-1) = byx(2:end,1:end-1) | dyx;
      byx(1:end-1,2:end) = byx(1:end-1,2:end) | dyx;
      % combine boundaries
      boundary = bx | by | bxy | byx;
   end

   %% return reordered color map
   %% (maximize distance between successive colors)
   function cmap = cmap_reorder(cmap)
      % initialize
      N = size(cmap,1);
      order = zeros([N 1]);
      used = zeros([N 1]);
      % fill color map
      order([1 2]) = [1 N];
      used([1 N]) = 1;
      pos = 3;
      step_size = N/2;
      while (step_size >= 1)
         for curr = step_size:step_size:N
            if (~used(curr))
               used(curr) = 1;
               order(pos) = curr;
               pos = pos + 1;
            end
         end
         step_size = step_size/2;
      end
      cmap = cmap(order,:);
   end

   % return color to use for a given number of labels
   function cmap = cmap_n(n_labels)
      n_colors = 64;
      while (n_colors < n_labels), n_colors = n_colors*2; end
      cmap = cmap_reorder(jet(n_colors));
   end
      
   % return colormap to use for given set of labels 
   function cmap = cmap_labels(labels)
      n_labels = max(labels(:));
      cmap = cmap_n(n_labels);
   end

   %% return color of the given object id
   function c = get_obj_color(data, n)
      c = data.state.cmap(n,:);
   end
   
   %% return name of the given object id
   function name = get_obj_name(data, n)
      name = data.image.obj_names{n};
   end
   
   %% compute pixel membership and centroids of each region
   function [pixel_ids centroids] = compute_membership(rmap)
      % compute pixel ids
      n_regions = max(rmap(:));
      pixel_ids = cell([1 n_regions]);
      pix_labels = reshape(rmap, [1 prod(size(rmap))]);
      [rmap_sorted inds] = sort(rmap(:));
      pix_labels = pix_labels(inds);
      pix_starts = find(pix_labels ~= [-1 pix_labels(1:end-1)]);
      pix_ends   = find(pix_labels ~= [pix_labels(2:end) (n_regions+1)]);
      for n = 1:length(pix_starts);
         ps = pix_starts(n);
         pe = pix_ends(n);
         pixel_ids{pix_labels(ps)} = inds(ps:pe);
      end
      % compute centroids
      centroids = zeros([n_regions 2]);
      [sx sy] = size(rmap);
      for n = 1:n_regions
         [xs ys] = ind2sub([sx sy], pixel_ids{n});
         centroids(n,1) = mean(xs);
         centroids(n,2) = mean(ys);
      end
   end
% ----------------------------------------------------
% Display functions

   %% create seeds-ucm overlay
   function img = seeds_ucm_overlay(ucm, seeds)
      % if ucm is quadruple size, scale it down
      if (size(ucm,1) >= 4.*size(seeds,1))
         ucm = ucm(1:2:end,1:2:end);
      end
      % resize ucm to original image size
      xs = 3:2:size(ucm,1);
      ys = 3:2:size(ucm,2);
      ucm = ...
         max(max(ucm(xs,ys),ucm(xs-1,ys)),max(ucm(xs,ys-1),ucm(xs-1,ys-1)));
      % get colormap
      cmap = cmap_labels(seeds);
      % assemble ucm + seeds overlay
      seed_inds = find(seeds > 0);
      seed_vals = seeds(seed_inds);
      img_r = ucm;
      img_g = ucm;
      img_b = ucm;
      img_r(seed_inds) = cmap(seed_vals,1);
      img_g(seed_inds) = cmap(seed_vals,2);
      img_b(seed_inds) = cmap(seed_vals,3);
      img = cat(3,img_r,img_g,img_b);
   end

   %% create seeds-ucm overlay (inverted)
   function img = seeds_ucm_overlay_inv(ucm, seeds)
      % if ucm is quadruple size, scale it down
      if (size(ucm,1) >= 4.*size(seeds,1))
         ucm = ucm(1:2:end,1:2:end);
      end
      % resize ucm to original image size
      xs = 3:2:size(ucm,1);
      ys = 3:2:size(ucm,2);
      ucm = ...
         max(max(ucm(xs,ys),ucm(xs-1,ys)),max(ucm(xs,ys-1),ucm(xs-1,ys-1)));
      ucm = 1 - ucm;
      % get colormap
      cmap = cmap_labels(seeds);
      % assemble ucm + seeds overlay
      seed_inds = find(seeds > 0);
      seed_vals = seeds(seed_inds);
      img_r = ucm;
      img_g = ucm;
      img_b = ucm;
      img_r(seed_inds) = cmap(seed_vals,1);
      img_g(seed_inds) = cmap(seed_vals,2);
      img_b(seed_inds) = cmap(seed_vals,3);
      img = cat(3,img_r,img_g,img_b);
   end

   %% create boundary-image overlay
   function im = boundary_overlay(im, labels)
      % resize labels to original image size
      labels_sm = resize_labels(labels);
      % compute boundary from labels
      boundary = boundary_from_labels(labels_sm);
      % create image + boundary overlay
      im = 0.5.*(im + repmat(boundary,[1 1 3]));
   end

   %% update main ui figure
   function disp_update_ui(ucm, seeds, fh)
      % create overlay
      img = seeds_ucm_overlay(ucm, seeds);
      % display overlay
      f = gcf;
      set(gcf,'Visible','off')  
      set(0, 'DefaultFigureVisible', 'off')
      set(0, 'DefaultAxesVisible', 'off')
      set(fh,'Visible','off') % all subsequent figures "off"
      figure(fh);
      image(img);
      axis image;
      set(gca,'XTick',[]);
      set(gca,'YTick',[]);
      colormap(cmap);
      title('Groundtruth')
      figure(f);
   end

   %% update segmentation results figure
   function disp_update_seg(labels, obj_names, fh)
      % resize labels to original image size
      labels_sm = resize_labels(labels);
      % get colormap
      cmap = cmap_labels(labels_sm);
      % find centroids
      [pixel_ids centroids] = compute_membership(labels_sm);
      % display labels
      f = gcf;
      set(gcf,'Visible','off')
      set(0, 'DefaultFigureVisible', 'off')
      set(0, 'DefaultAxesVisible', 'off')
      set(fh,'Visible','off')% all subsequent figures "off"
      figure(fh);    
      image(labels_sm);
      axis image;
      axis off;
      title('Segmentation');
      colormap(cmap);
      % display object names
      hold on;
      for n = 1:length(pixel_ids)
         x = centroids(n,1);
         y = centroids(n,2);
         o_name = obj_names{n};
         if (~isempty(o_name))
            h = text(y,x,o_name);
            set(h,'Color',[1 1 1]);
         end
      end
      hold off;
      set(f,'Visible','off')% all subsequent figures "off"
      set(0, 'DefaultFigureVisible', 'off')
      set(0, 'DefaultAxesVisible', 'off')
      figure(f);
   end

   %% update boundary results figure
   function disp_update_boundary(im, labels, fh)
      % create image + boundary overlay
      im = boundary_overlay(im, labels);
      % display overlay
      f = gcf;
      set(gcf,'Visible','off')
      set(0, 'DefaultFigureVisible', 'off')
      set(0, 'DefaultAxesVisible', 'off')
      set(fh,'Visible','off')% all subsequent figures "off"% all subsequent figures "off"
      figure(fh);
      image(im);
      axis image;
      axis off;
      title('Boundary Map');
      set(f,'Visible','off')% all subsequent figures "off"
      figure(f);
   end

   %% update all figure content
   function disp_update(data)
      dimg = data.image;
      disp_update_ui(dimg.ucm, dimg.seeds, data.figures.fh);
      disp_update_seg(dimg.labels, dimg.obj_names, data.figures.fh_seg);
      disp_update_boundary(dimg.im, dimg.labels, data.figures.fh_bdry);
   end

function X = imNormalize( X, flag )
% Various ways to normalize a (multidimensional) image.
%
% X may have arbitrary dimension (ie an image or video, etc).  X is treated
% as a vector of pixel values.  Hence, the mean of X is the average pixel
% value, and likewise the standard deviation is the std of the pixels from
% the mean pixel.
%
% USAGE
%  X = imNormalize( X, flag )
%
% INPUTS
%  X       - n dimensional array to standardize
%  flag    - [1] determines normalization procedure. Sets X to:
%            1: have zero mean and unit variance
%            2: range in [0,1]
%            3: have zero mean 
%            4: have zero mean and unit magnitude
%            5: zero mean/unit variance, throws out extreme values 
%               and also normalizes to [0,1]
%
% OUTPUTS
%  X       - X after normalization.
%
% EXAMPLE
%  I = double(imread('cameraman.tif'));
%  N = imNormalize(I,1);
%  [mean(I(:)), std(I(:)), mean(N(:)), std(N(:))]
%
% See also FEVALARRAYS
%
% Piotr's Computer Vision Matlab Toolbox      Version 2.0
% Copyright 2014 Piotr Dollar.  [pdollar-at-gmail.com]
% Licensed under the Simplified BSD License [see external/bsd.txt]

if (isa(X,'uint8')); X = double(X); end
if (nargin<2 || isempty(flag)); flag=1; end
siz = size(X);

if( flag==1 || flag==3 || flag==4 )
  % set X to have zero mean
  X = X(:);  n = length(X);
  meanX = sum(X)/n;
  X = X - meanX;

  % set X to have unit std
  if( flag==1 || flag==4 )
    sumX2 = sum(X.^2);
    if( sumX2>0 )
      if( flag==4 )
        X = X / sqrt(sumX2);
      else
        X = X / sqrt(sumX2/n);
      end
    end
  end
  X = reshape(X,siz);

elseif(flag==2)
  % set X to range in [0,1]
  X = X - min(X(:));  X = X / max(X(:));

elseif( flag==5 )
  X = imNormalize( X, 1 );
  t=2;
  X( X<-t )= -t;
  X( X >t )=  t;
  X = X/2/t + .5;

else
  error('Unknown standardization procedure');
end
end





end
