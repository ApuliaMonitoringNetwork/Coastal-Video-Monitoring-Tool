function [value,alarm1, alarm2] = Sea_level_filter(level_input,filterpars,outputstr,timestamp_input, long_term_sealevel)

%%filter for mareographic data using a temporal window 16 hours around the 
%time interval needed, so LENGTH(LEVEL)=65

% INPUT:   
%           level_input = json file with sea level only
%           filterpars  = mat file with minpar = minimum value admitted, ...
%           outputstr = output json file to save on
%           timestamp_input = i.e. '1450267200'

% OUTPUT:Depth_000030_002
%           value = value aat the center of the level_input vctr FILTERED
%           alarm1 = text variable displaying info regarding value over
%           tolerance

%           alarm2 = text variable displaying info regarding spyke, using
%           spline adaptation of the vector.

%% LOADING DATA
try 
    levell=loadjson(level_input); 
catch
    
%     -0.13 
%     VALID FOR PORTOCESAREO
    value=long_term_sealevel;
%    
    if nargin==3 && isstr(outputstr)
    opt.FileName=outputstr
    savejson('',value,opt);
    else
    opt.FileName=fullfile(pwd,'sea_level.json')
    savejson('',value,opt);
end
    error('Problem using function import jsonlevelValues.  Assigning a value of long terms mean value = -0.13');
end
try 
    params=load(filterpars);
catch
%     valido per TorreLapillo solamente
    value=long_term_sealevel;
    if nargin==3 && isstr(outputstr)
    opt.FileName=outputstr
    savejson('',value,opt);
else
    opt.FileName=fullfile(pwd,'sea_level.json')
    savejson('',value,opt);
end
    error('Problem using function import Parameters MAT files.  Assigning a value of long terms mean value = -0.13');
end

clear timest
% code M01TLE valid for TorreLapillo
if strcmp(levell.device,'M01TLE')
    disp 'ok device'
    for i = 1:numel(levell.records)
        if isempty(levell.records{i}.timestamp)
        timest(i) = NaN;
        else
        timest(i) = levell.records{i}.timestamp;
        end
        if isempty(levell.records{i}.value)
            level(i)= NaN;
        else
        level(i) = levell.records{i}.value;
        end
        if diff(timest)<=0
            timest=fliplr(timest);
        end
        
    end


%% Codice
level_ori=level;
i= length(level);
alarm1=0;
alarm2=0;



indexNaN=find(isnan(level))';
if indexNaN
%     level(indexNaN)=[];
    i=length(level);
    disp(['problem with NaN! N: ' num2str(numel(indexNaN)) ])
end
if mod(i,2)==0 
    disp('Problem in number of sea_level values! ')
    pari=1;
else
    pari=0;
end
if i ~=65
    % i ~=33
    disp('Not real number of data gave!')
    not_rv=1;
else 
    not_rv=0;
end
          
    

% da verificare come vengono dati i dati
% level=timeserie;
time=timest;

level1=level(3/2*params.maxpar<level<3/2*params.minpar);
index_soglie=level(level1);
if isempty(index_soglie)

else
    level(index_soglie)=params.longterm_mean;
end
%% tolleranza
% toll=(2*pi*delta*30)/720;
toll=((2*pi*params.delta*30)/720)*2;

ree=diff(level);
%% differe=ree(ree>toll);
index_toll=find(ree>toll | ree<-toll )';
if isempty(index_toll)
else 
    [value F H]=unique(level);
    rep=diff(find(diff([-Inf sort(H') Inf])));
    %10 repetition consentite
    repeated=value(rep>30);
    if repeated
        level(level==repeated)=params.longterm_mean;
        alarm1=(['problem, check values! more than 10 repetition! N:  ', num2str(numel(repeated))])
    else
        level(index_toll+1)=params.longterm_mean;
        alarm1=(['Values over tollerances, shifted at ' num2str(params.longterm_mean) '! N: ', num2str(numel(index_toll))])
    end
end

%% fitting curve on data
[f,goodness,output]=fit(time',level','smoothingspline','Exclude',[index_toll],'Exclude',[indexNaN]);

%% check here for plotting
% % h1=figure('visible','off');
%  h1=figure,
%  subplot(1,2,1);plot(f,time,level);
% % h2= figure('visible','off'); 
% % h1=figure,
%  subplot(1,2,2);plot(f,time',level','residuals');

%% continue fitting
for k =1:length(level)-numel(indexNaN)-numel(index_toll)
if output.residuals(k)>5*std(output.residuals)

level(k)=params.longterm_mean;
alarm2=(['find values do not match spline! N: ', num2str(numel(k))])
end
end


if numel(level)==numel(level_ori);
if not_rv && pari
    disp 'not real length of values'
    ind=length(level)/2;
    value=level(ind);
    time_st=time(ind);
elseif not_rv || ~not_rv
    
    ind=length(level)/2+0.5;
    value=level(ind);time_st=time(ind);
end
else
    if mod(length(level_ori),2)==0;
        if isnan(level(ind)) 
            value=params.longterm_mean;
            time_st=time(ind);
        else
        value=level(ind);
        time_st=time(ind);
        end
    else mod(length(level_ori),2)==0;
        if level_ori(ind);
            value=params.longterm_mean;
            time_st=time(ind);
        else
        value=level_ori(ind);
        time_st=time(ind);
        end
    end
        
end 


%% time checking
diff_time=(str2num(timestamp_input)-time_st);
if abs(diff_time)<=7200;
    disp('Ok timestamp')
else 
    disp(['Problem in datetime value: input - centervalue = ', num2str(diff_time/60) ,' min; ... value at longtermMean'])
    value=params.longterm_mean;
end


% figures to be deleted, inutiles
% figure(h1),subplot(1,2,2);plot(f,time,level);
% figure(h2),subplot(1,2,2);plot(f,time,level,'residuals');
% figure,plot(time,level_ori,'ro');
% print value

% outputstr=value;
%% da sostituire con il salvataggio savejson (vedi sotto)
if isnan(value)
    value=params.longterm_mean;
end
disp(value)
%% savejson 
if time_st
    json_mesh=struct('value',value,'timestamp',time_st);
else 
    json_mesh=struct('value',value);
end
 if nargin>=3 && isstr(outputstr)
     opt.FileName=outputstr;
     savejson('',json_mesh,opt);
 else
     opt.FileName=fullfile(pwd,'sea_level.json')
     savejson('',json_mesh,opt);
 end
else
    disp 'device not correct'
end
