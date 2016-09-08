%% call Fiji, software for preprocessing without open it;
% it would be necessary to do: 
% javaaddpath('C:\fiji.app\jars\mij-1.3.6-fiji2.jar');
% javaaddpath('C:\fiji.app\jars\ij-1.49v.jar');
% open image from whatever and get title
% MIJ.run('Open...');
%clear all, clc, close all

%[image,name,path]=mijread;
MIJ.run('Open...',jstr);
%     dlg = ij.io.OpenDialog('Select an image', '');
tit=MIJ.getCurrentTitle();
% Preprocessing Steps
MIJ.run('Enhance Contrast...', 'saturated=0.4 equalize');
MIJ.run('Enhance Contrast...', 'saturated=15');
MIJ.run('Remove Outliers...', 'radius=4 threshold=5 which=Dark');
MIJ.run('RGB to CIELAB');
tit_1=MIJ.getCurrentTitle();
MIJ.selectWindow(tit_1);
MIJ.run('Stack to Images');
MIJ.selectWindow('b');
MIJ.run('BEEPS ', 'range_filter=sech photometric_standard_deviation=3.2 spatial_decay=0.0040 iterations=3');
% MIJ.run('Close', ['2006.11.25_09.01.34MediaTc1' -1 '.jpg']);
% MIJ.run('Images to Stack', 'name=fdsfdsfdsf title=[] use');
% MIJ.run('RGB to CIELAB');
MIJ.selectWindow(tit);
MIJ.run('Close');
MIJ.run('Images to Stack', 'name=cielab title=[] ');
MIJ.selectWindow('cielab');
MIJ.run('RGB to CIELAB');

MIJ.selectWindow('cielab');
MIJ.run('Close');
MIJ.selectWindow('cielab RGB');

%im=MIJ.getImage('cielab RGB');
%im=uint8(im);
na=char(tit);
%jstr=java.lang.String(['path=[' cd '/' file ']' ]);
jstrsave=java.lang.String([ 'save=[' pat '/' na '_prepro.jpg]']);

MIJ.run('Save',jstrsave);
%imwrite(im,([cd '/' na,'_prepro.jpg']),'JPEG','Quality',100);
imgFile=([pat '/' na,'_prepro.jpg']);
MIJ.closeAllWindows();

% MIJ.selectWindow(' RGB');
