function Miji_exe(open_imagej,fiji_dir)


    %% This script sets up the classpath to Fiji and optionally starts MIJ
    % Author: Nico Valentini, Jacques Pecreaux, Johannes Schindelin, Jean-Yves Tinevez

    if nargin < 1
        open_imagej = true;
    end
    

    %% Get the Fiji directory
	if nargin > 1
		fiji_directory = fiji_dir;
	else
		fiji_directory = (['/home/nico/Scaricati/Fiji.app']); 
    end
    

    %% Get the Java classpath
    classpath = javaclasspath('-all');

    %% Add all libraries in jars/ and plugins/ to the classpath
    
    % Switch off warning
%     fiji_directory = fileparts(fileparts(mfilename('fullpath')));

    warning_state = warning('off');
    
    add_to_classpath(classpath, fullfile(fiji_directory,'jars'));
    add_to_classpath(classpath, fullfile(fiji_directory,'plugins'));
    
    % Switch warning back to initial settings
    warning(warning_state)

    % Set the Fiji directory (and plugins.dir which is not Fiji.app/plugins/)
    java.lang.System.setProperty('ij.dir', fiji_directory);
    java.lang.System.setProperty('plugins.dir', fiji_directory);

    %% Maybe open the ImageJ window
    if open_imagej
        cd ..;
        fprintf('\n\nUse MIJ.exit to end the session\n\n');
        MIJ.start();
    else
        % initialize ImageJ with the NO_SHOW flag (== 2)
        ij.ImageJ([], 2);
    end

    % Make sure that the scripts are found.
    % Unfortunately, this causes a nasty bug with MATLAB: calling this
    % static method modifies the static MATLAB java path, which is
    % normally forbidden. The consequences of that are nasty: adding a
    % class to the dynamic class path can be refused, because it would be
    % falsy recorded in the static path. On top of that, the static
    % path is fsck in a weird way, with file separator from Unix, causing a
    % mess on Windows platform.
    % So we give it up as now.
    % %    fiji.User_Plugins.installScripts();
end

function add_to_classpath(classpath, directory)
    % Get all .jar files in the directory
    dirData = dir(directory);
    dirIndex = [dirData.isdir];
    jarlist = dir(fullfile(directory,'*.jar'));
    path_= cell(0);
    for i = 1:length(jarlist)
      %disp(jarlist(i).name);
        if not_yet_in_classpath(classpath, jarlist(i).name)
            path_{length(path_) + 1} = fullfile(directory,jarlist(i).name);
        end
    end

    %% Add them to the classpath
    if ~isempty(path_)
        javaaddpath(path_, '-end');
    end

    %# Recurse over subdirectories
    subDirs = {dirData(dirIndex).name};
    validIndex = ~ismember(subDirs,{'.','..'});

    for iDir = find(validIndex)
      nextDir = fullfile(directory,subDirs{iDir});
      add_to_classpath(classpath, nextDir);
    end
end

function test = not_yet_in_classpath(classpath, filename)
%% Test whether the library was already imported
expression = strcat([filesep filename '$']);
test = isempty(cell2mat(regexp(classpath, expression)));
end
