<?php

if(	!isset($_POST['from']) || 
	!isset($_POST['to']) || 
	!isset($_POST['files']) || 
	!isset($_POST['prefix']) || 
	!is_array($_POST['files']) ||
	!count($_POST['files']) ||
	!is_array($_POST['prefix']) ||
	!count($_POST['prefix'])
) {
	header("HTTP/1.0 400 Bad request");
	echo "Missing required data";
	die;
}

$fromDate = strtotime($_POST['from']);
$toDate = strtotime($_POST['to']);
if($toDate<$fromDate)
{
	header("HTTP/1.0 400 Bad request");
	echo "Leading date must be prior to trailing date";
	die;
}


$zip = new ZipArchive();
$filename = "archive.zip";

if ($zip->open($filename, ZipArchive::CREATE)!==TRUE) {
    header("HTTP/1.0 500 Internal Server Error");
    die;
}

if($key = array_search("shoreline",$_POST['files'])) {
	unset($_POST['files'][$key]);
	$_POST['files'][] = 'shoreline.shp';
	$_POST['files'][] = 'shoreline.shx';
	$_POST['files'][] = 'shoreline.dbf';
	$_POST['files'][] = 'shoreline.prj';
}

$dateIt = $fromDate;
while($dateIt<=$toDate) {
	$currentDir = date("Y/m/d/H/i",$dateIt);
	foreach($_POST['prefix'] as $prefixIdx => $prefix) {
		foreach($_POST['files'] as $fileIdx => $file) {
			$targetFile = 'resources/'.$currentDir . '/' . $prefix . '_' . $file;
			if(file_exists($targetFile))
				$zip->addFile($targetFile);
		}
	}
	$dateIt = strtotime("+30 minutes",$dateIt);
}

$zip->close();

header("Content-Type: application/zip");
header("Content-Disposition: attachment; filename=$filename");
header("Content-Length: " . filesize($filename));
readfile($filename);

unlink($filename);
?>

