var imagesType = ['Snapshot','Averaged','Standard deviation','Geo rectified']
var sitesDesc = {
	'Fasano (Torre Canne)' : {
		'cameras' : ['vs1','th1']
	},
	'Port Cesareo (Torre Lapillo)' : {
		'cameras' : ['vs1','vs2','merged rectified']
	}
}
//Image report, shoreline map and download
var $DataTable
var canvasWidth
var canvasHeight
var shorelinesImg
var shorelines = {}
var selectedShorelines = {}
var shorelinesToDownload = 0
var shorelinePixelLength	/// jgw: length of a pixel in the x direction (horizontal)
var shorelinePixelHeight	/// jgw: length of a pixel in the y direction (vertical) 
var shorelineTopLeftPoint
var screenRatio
var $xCoord 
var $yCoord
var $coordArea
var $mapsCanvas
var $transectCanvas
var transectImg
var transects = {}

var MAPS_AXIS_SIZE = 200 /// in canvas pixel, will be set at run time depending on font site ans screen to canvas aspect ratio
var MAPS_AXIS_SIZE_SCREEN = 200; /// in screen pixel, will be set at run time
var MAPS_ARROW_HEIGHT = 40
var MAPS_ARROW_WIDTH = 20
var MAPS_AXIS_COLOR = '#999999'
var MAPS_AXIS_WIDTH = 5
var MAPS_SHORELINE_WIDTH = 8
var MAPS_AXIS_GRADUATION = 100	/// in meter
var MAPS_AXIS_FONT_SIZE = 13
var MAPS_AXIS_FONT_TYPE = 'Arial'
var MAPS_AXIS_TICK_SIZE = 20
var TRANSECT_LINE_WIDTH = 3
var TRANSECT_COLOR = '#1AA3FF'
var TRANSECT_SELECTED_COLOR = '#FFFF4D'
var TRANSECT_Y_AXIS_EPSILON = 0.5

var $progressbar;
var $progressLabel;

var buildResPath = function(desc) {
	var prefix = desc.prefix;
	var site = desc.site.toLowerCase().replace(/ /g,'_')
	var camera = desc.camera.toLowerCase().replace(/ /g,'_')
	var type = desc.type ? '_'+desc.type.toLowerCase().replace(/ /g,'_') : ''
	var ext = (desc.ext ? desc.ext : 'jpg')
	return 'resources/'+prefix+'/'+site+'_'+camera+type+'.'+ext
}
// variable added (NICO V.) in order to cope for the X Y coordinate reading in the "Shoreline Map " TAB
var buildResPath_georect = function(desc) {
	var prefix = desc.prefix;
	var site = desc.site.toLowerCase().replace(/ /g,'_')
	var camera = desc.camera.toLowerCase().replace(/ /g,'_')
	var type = desc.type ? '_'+desc.type.toLowerCase().replace(/ /g,'_') : ''
	var ext = (desc.ext ? desc.ext : 'jpg')
	return 'resources/'+prefix+'/'+site+'_'+camera+'_'+'geo_rectified'+'.'+ext
}
//
var createImageTypeSelector = function(siteName, cameraName) {
	$imagesType = $('<select>')
					.attr('class','right');
	$.each(imagesType,function(idx,name){
		$('<option/>')
			.append(name)
			.appendTo($imagesType)
	});
	$imagesType.on('change', function(e) {
		//.attr('height','375')
		var $img = $(this).next().children('img')
		$img.on('load',function() {
			$img.attr('height','');
		}).on('error',function() {
			$img.attr('height','375');
		});
		$img.attr('src',buildResPath({
							'prefix': 'current',
							'site': siteName,
							'camera': cameraName,
							'type': $(this).val()
						}))
	});
	return $imagesType;
}
var createDirectoryFromDate = function(aDate) {
	var YYYY = aDate.getFullYear()
	var MM = aDate.getMonth()+1
	if(MM<10) MM='0'+MM
	var dd = aDate.getDate()
	if(dd<10) dd='0'+dd
	var hh = aDate.getHours()
	if(hh<10) hh='0'+hh
	var mm = aDate.getMinutes()
	if(mm<10) mm='0'+mm
	return YYYY+'/'+MM+'/'+dd+'/'+hh+'/'+mm
}
var toUserDate = function(aDirPath) {
	var items = aDirPath.split('/')
	return items[0]+'/'+items[1]+'/'+items[2]+' '+items[3]+':'+items[4];
}
var loadImageReport = function() {
	var site = $('#imagesReport_sites > input[name=imagesReport_sites]:checked').val();
	var $siteLabel = $('#imagesReport_site_label');
	if(typeof(site)==='undefined') {
		$siteLabel.addClass('error');
		return;
	}
	$siteLabel.removeClass('error');
	
	var _30Min=30*60*1000
	var fromDateStr = $('#imagesReport_from').val()
	if(fromDateStr=='') {
		$('#imagesReport_from_label').addClass('error');
		return;
	}
	$('#imagesReport_from_label').removeClass('error');
	var fromDate = new Date(fromDateStr)
	var from = new Date(_30Min*(fromDate.getTime()/_30Min).toFixed(0))
	
	var endDateStr = $('#imagesReport_to').val()
	if(endDateStr=='') {
		$('#imagesReport_to_label').addClass('error');
		return;
	}
	$('#imagesReport_to_label').removeClass('error');
	var endDate = new Date(endDateStr)
	var end = new Date(_30Min*(endDate.getTime()/_30Min).toFixed(0))
	if(end.getTime()<=from.getTime()) {
		$('#imagesReport_from_label').addClass('error');
		return;
	}
	$('#imagesReport_from_label').removeClass('error');
	
	var interval = $('#imagesReport_interval > input[name=imagesReport_interval]:checked').val()
	
	
	var imgType = [];
	if($('#imagesReport_types_snapshot').is(':checked')) imgType.push('Snapshot')
	if($('#imagesReport_types_averaged').is(':checked')) imgType.push('Averaged')
	if($('#imagesReport_types_std_dev').is(':checked')) imgType.push('Standard deviation')
	if($('#imagesReport_types_geo_rectified').is(':checked')) imgType.push('Geo rectified')
	if(!imgType.length) {
		$('#imagesReport_type_label').addClass('error');
		return;
	}
	else
		$('#imagesReport_type_label').removeClass('error');
	
	/// Generate dates list
	var datesList = []
	while(true) {
		var dir = createDirectoryFromDate(from);
		datesList.push(dir)
		from = new Date(from.getTime()+(interval*60*1000))
		if(from.getTime()>end.getTime()) break;
	}
		
	$table = $('#imagesReport_table')
	$table.hide()
	$tableContent = $('#imagesReport_table>tbody')
	if($DataTable) $DataTable.destroy();
	$tableContent.empty()
	
	var cameras = sitesDesc[site].cameras;
	for(var i=0;i<datesList.length;i++) {
		for(var j=0;j<imgType.length;j++) {
			var isGeoRect = (imgType[j]=='Geo rectified')
			for(var k=0;k<cameras.length;k++) {
				$('<tr/>')
					.append(
						$('<td/>')
							.append(toUserDate(datesList[i]))
					)
					.append(
						$('<td/>')
							.append(cameras[k])
					)
					.append(
						$('<td/>')
							.append(imgType[j])
					)
					.append(
						$('<td/>')
							.append(
								$('<img>')
									.attr('src',buildResPath({
											'site': site,
											'camera': cameras[k],
											'type': imgType[j],
											'prefix': datesList[i]
										})
									)
									.attr('width',160)
									.attr('height',isGeoRect?120:90)
									.attr('data-title',site+' '+cameras[k]+' @ '+datesList[i]+' ['+imgType[j]+']')
									.on('click',function(e) { showImg(e) })
							)
					)
					.appendTo($tableContent);
			}
		}
	}

	$DataTable = $table.DataTable({'lengthMenu': [ 3, 5, 10, 25, 50, 100 ]})
	$table.show()
	return true;
}
var showImg = function(e) {
	var imgTitle = e.target.dataset['title']
	$('#image_dialog > img').attr('src',e.target.src)
	$('#image_dialog > img').attr('data-title',e.target.dataset.title)
	$d = $('#image_dialog')
	$d.dialog('option','title',imgTitle)
	$d.dialog('open');
}
var loadMapsReport = function() {
	$coordArea.hide()
	$mapsCanvas.hide()
	$('#mapsReport_shoreline').empty()
	
	var site = $('#mapsReport_sites > input[name=mapsReport_sites]:checked').val();
	var $siteLabel = $('#mapsReport_site_label');
	if(typeof(site)==='undefined') {
		$siteLabel.addClass('error');
		return;
	}
	$siteLabel.removeClass('error');
	
	var camera = $('#mapsReport_cameras').val()
	if(camera == "") {
		$('#mapsReport_cameras_label').addClass('error')
		return
	}
	$('#mapsReport_cameras_label').removeClass('error')
	
	var _30Min=30*60*1000
	var fromDateStr = $('#mapsReport_from').val()
	if(fromDateStr=='') {
		$('#mapsReport_from_label').addClass('error');
		return;
	}
	$('#mapsReport_from_label').removeClass('error');
	var fromDate = new Date(fromDateStr)
	var from = new Date(_30Min*(fromDate.getTime()/_30Min).toFixed(0))
	var endDateStr = $('#mapsReport_to').val()
	if(endDateStr=='') {
		$('#mapsReport_to_label').addClass('error');
		return;
	}
	$('#mapsReport_to_label').removeClass('error');
	var endDate = new Date(endDateStr)
	var end = new Date(_30Min*(endDate.getTime()/_30Min).toFixed(0))	
	if(end.getTime()<=from.getTime()) {
		$('#mapsReport_from_label').addClass('error');
		return;
	}
	$('#mapsReport_from_label').removeClass('error');
	
	/// Generate dates list
	var dateIt = from
	var datesList = []
	while(true) {
		var dir = createDirectoryFromDate(dateIt);
		datesList.push(dir)
		dateIt = new Date(dateIt.getTime()+_30Min)
		if(dateIt.getTime()>end.getTime()) break;
	}

	
	/// Draw georectified image
	var geoRectifiedImage = buildResPath({
			'prefix': createDirectoryFromDate(from),
			'site': site,
			'camera': camera,
			'type': 'Geo rectified'
		})
	shorelinesImg = document.createElement('img');
	shorelinesImg.onload = function() {
		var canvas = $mapsCanvas[0]
		var context = canvas.getContext('2d')
		
		/// Adapt the arequired axis border
		var tmpScreenRatio = this.width/$(canvas).width()	/// Do not include axis size to approximate axis size
		
		MAPS_AXIS_SIZE = computeRequiredAxisSpace(canvas,context,tmpScreenRatio)
		MAPS_AXIS_SIZE_SCREEN = MAPS_AXIS_SIZE/screenRatio
		screenRatio = (this.width+MAPS_AXIS_SIZE)/$(canvas).width() /// Recompute real screen ratio adding axis size
		
		canvas.width = this.width+MAPS_AXIS_SIZE
		canvas.height = this.height+MAPS_AXIS_SIZE
		var aspectRatio = this.width / this.height
		canvas.style.height = (parseInt(canvas.style.height) / aspectRatio)+' px'
		context.drawImage(this, MAPS_AXIS_SIZE, 0)
		drawAxis(canvas,context)

		$mapsCanvas.show()
		$coordArea.show()
		loadShoreline(site,camera,datesList)
	}
	shorelinesImg.onerror = function() {
		console.log(this.src+' does not exists!')
	}
	shorelinesImg.src = geoRectifiedImage
	
	return true;
}
var loadTransectsReport = function() {
	var site = $('#transectReport_sites > input[name=transectReport_sites]:checked').val();
	var $siteLabel = $('#transectReport_site_label');
	if(typeof(site)==='undefined') {
		$siteLabel.addClass('error');
		return;
	}
	$siteLabel.removeClass('error');
	
	var camera = $('#transectReport_cameras').val()
	if(camera == "") {
		$('#transectReport_cameras_label').addClass('error')
		return
	}
	$('#transectReport_cameras_label').removeClass('error')
	
	var _30Min=30*60*1000
	var fromDateStr = $('#transectReport_from').val()
	if(fromDateStr=='') {
		$('#transectReport_from_label').addClass('error');
		return;
	}
	$('#transectReport_from_label').removeClass('error');
	var fromDate = new Date(fromDateStr)
	var from = new Date(_30Min*(fromDate.getTime()/_30Min).toFixed(0))
	var endDateStr = $('#transectReport_to').val()
	if(endDateStr=='') {
		$('#transectReport_to_label').addClass('error');
		return;
	}
	$('#transectReport_to_label').removeClass('error');
	var endDate = new Date(endDateStr)
	var end = new Date(_30Min*(endDate.getTime()/_30Min).toFixed(0))	
	if(end.getTime()<=from.getTime()) {
		$('#transectReport_from_label').addClass('error');
		return;
	}
	$('#transectReport_from_label').removeClass('error');
	
	/// Generate dates list
	var dateIt = from
	var datesList = []
	while(true) {
		var dir = createDirectoryFromDate(dateIt);
		datesList.push(dir)
		dateIt = new Date(dateIt.getTime()+_30Min)
		if(dateIt.getTime()>end.getTime()) break;
	}

	
	/// Draw src image
	var imagePath = buildResPath({
			'prefix': createDirectoryFromDate(from),
			'site': site,
			'camera': camera,
			'type': 'Snapshot'
		});
	transectImg = document.createElement('img')
	transectImg.onload = function() {
		var canvas = $transectCanvas[0]
		var context = canvas.getContext('2d')		
		canvas.width = this.width
		canvas.height = this.height
		var aspectRatio = this.width / this.height
		canvas.style.height = (parseInt(canvas.style.height) / aspectRatio)+' px'
		context.drawImage(this, 0, 0)
		updateTransectsLegend(site,camera);
		$transectCanvas.show()
	}
	transectImg.onerror = function() {
		console.log(this.src+' does not exists!')
	}
	transectImg.src = imagePath
}
var loadShoreline = function(site,camera,datesList) {
	shorelines = {}
	selectedShorelines = {}
	shorelinesToDownload = datesList.length
	console.log('Have to download '+shorelinesToDownload+' shorelines');
	/// Load jgw file
	// buildResPath substitute by buildResPath_georect (NICO VAL)
	var jgwFileName = buildResPath_georect({
			'site': site,
			'camera': camera,
			'prefix': datesList[0],
			'ext': 'jgw'
		});
	//
	$.ajax({
		url: jgwFileName,
		mimeType: 'text/plain'
	}).done(function(data) {
		var items = data.split('\n')
		shorelinePixelLength = items[0]
		shorelinePixelHeight = items[3]
		shorelineTopLeftPoint = {
			x: Number(items[4]),
			y: Number(items[5])
		}
		renderShoreLine()
	}).fail(function() {
		shorelinePixelLength = shorelinePixelHeight = shorelineTopLeftPoint = null;
	});
	
	/// Load each shorelines
	$.each(datesList,function(idx,date) {
		var shorelineName = buildResPath({
				'site': site,
				'camera': camera,
				'prefix': date,
				'type': 'shoreline',
				'ext': 'json'
			});
		$.getJSON(shorelineName, function( data ) {
			shorelines[date] = data
			if(--shorelinesToDownload==0) updateShorelineMenu()
		}).fail(function() {
			if(--shorelinesToDownload==0) updateShorelineMenu()
		})
	})
}
var updateShorelineMenu = function() {
	if(!shorelinesToDownload) {
		$legend = $('#mapsReport_shoreline')
		$('<button/>')
			.addClass('right')
			.append('Save as')
			.on('click', function() {
				exportShorelines()	
			})
			.button()
			.appendTo($legend)
			
		$ul = $('<ul>')
		dateSorted = Object.keys(shorelines).sort()
		$.each(dateSorted,function(id,date) { /// date, data
			$li = $('<li>')
			$('<input/>')
				.attr('type','checkbox')
				.attr('id',date)
				.on('click',function(e) { 
					drawShoreline(e)
				})
				.appendTo($li)
			$('<label/>')
				.attr('for',date)
				.append(date)
				.appendTo($li)
			var color = '#'+Math.floor(Math.random()*16777215).toString(16);
			shorelines[date]['color'] = color
			$('<span/>')
				.css('background-color',color)
				.appendTo($li)
			$li
				.appendTo($ul)
		})
		$ul.appendTo($legend)
	}
}

var drawShoreline = function(e) {
	$cb = $(e.target)
	if($cb.prop('checked'))
		selectedShorelines[$cb.attr('id')] = shorelines[$cb.attr('id')]
	else
		delete selectedShorelines[$cb.attr('id')]
	renderShoreLine()
}
var renderShoreLine = function() {
	canvas = $mapsCanvas[0]
	ctx = canvas.getContext('2d')
	canvasWidth = canvas.width
	canvasHeight = canvas.height
	ctx.clearRect(0, 0, canvasWidth, canvasHeight)
	ctx.fillStyle = "#ffffff";
	ctx.fillRect(0,0,canvasWidth,canvasHeight);
	ctx.drawImage(shorelinesImg, MAPS_AXIS_SIZE, 0 )
	drawAxis(canvas,ctx)
	var canvasSize = {
		width: canvasWidth-MAPS_AXIS_SIZE,
		height: canvasHeight-MAPS_AXIS_SIZE
	}
	ctx.lineWidth = MAPS_SHORELINE_WIDTH
	$.each(selectedShorelines, function(date,shoreline) {
		var sl = shoreline
		ctx.beginPath()
		var pts = sl.shoreline
		for(var i=0;i<pts.length-1;i++) {
			var from = toPixel(pts[i],canvasSize)
			ctx.moveTo(from.x+MAPS_AXIS_SIZE,from.y)
			var to = toPixel(pts[i+1],canvasSize)
			ctx.lineTo(to.x+MAPS_AXIS_SIZE,to.y)
		}
		ctx.strokeStyle = sl.color
		ctx.stroke()
	})
}
var drawAxis = function(c,ctx) {
	ctx.beginPath()
	ctx.moveTo(MAPS_AXIS_SIZE,0)
	ctx.lineTo(MAPS_AXIS_SIZE,c.height-MAPS_AXIS_SIZE)
	ctx.lineTo(c.width,c.height-MAPS_AXIS_SIZE)
	ctx.strokeStyle = MAPS_AXIS_COLOR
	ctx.lineWidth = MAPS_AXIS_WIDTH
	ctx.stroke()
	
	ctx.beginPath()
	ctx.moveTo(MAPS_AXIS_SIZE,0)
	ctx.lineTo(MAPS_AXIS_SIZE,MAPS_ARROW_HEIGHT)
	ctx.lineTo(MAPS_AXIS_SIZE-MAPS_ARROW_WIDTH, MAPS_ARROW_HEIGHT)
	ctx.closePath()
	ctx.stroke()
	ctx.fillStyle = MAPS_AXIS_COLOR
	ctx.fill()
	
	ctx.beginPath()
	ctx.moveTo(c.width, c.height-MAPS_AXIS_SIZE)
	ctx.lineTo(c.width-MAPS_ARROW_HEIGHT, c.height-MAPS_AXIS_SIZE)
	ctx.lineTo(c.width-MAPS_ARROW_HEIGHT, c.height-MAPS_AXIS_SIZE+MAPS_ARROW_WIDTH)
	ctx.closePath()
	ctx.stroke()
	ctx.fillStyle = MAPS_AXIS_COLOR
	ctx.fill()
	
	/// Compute ratio between image size and rendering area
	var pixelRatio = c.width/c.clientWidth
	ctx.textAlign = 'center'
	ctx.textBaseline = 'hanging'
	ctx.font = (MAPS_AXIS_FONT_SIZE*pixelRatio)+'px '+MAPS_AXIS_FONT_TYPE
	ctx.fillText(0,MAPS_AXIS_SIZE-MAPS_AXIS_TICK_SIZE,c.height-MAPS_AXIS_SIZE+MAPS_AXIS_TICK_SIZE)
	var _50ScreenPixel = 50*pixelRatio
	var i = 1;
	if(shorelinePixelLength) {
		var xGraduationLength = MAPS_AXIS_GRADUATION*(1/shorelinePixelLength)
		var xFrom = MAPS_AXIS_SIZE
		var xTo = c.width-_50ScreenPixel
		var y = c.height-MAPS_AXIS_SIZE
		while(true) {
			xFrom += xGraduationLength
			if(xFrom>xTo) break;
			ctx.beginPath()
			ctx.moveTo(xFrom,y-MAPS_AXIS_TICK_SIZE)
			ctx.lineTo(xFrom,y+MAPS_AXIS_TICK_SIZE)
			ctx.stroke()
			ctx.fillText((i++*MAPS_AXIS_GRADUATION)+'m',xFrom,y+MAPS_AXIS_TICK_SIZE)
		}
	}
	if(shorelinePixelHeight) {
		ctx.textAlign = 'right'
		ctx.textBaseline = 'middle'
		i = 1
		var yGraduationLength = MAPS_AXIS_GRADUATION*(1/shorelinePixelHeight);
		var x = MAPS_AXIS_SIZE-MAPS_AXIS_TICK_SIZE
		var yFrom = c.height-MAPS_AXIS_SIZE
		var yTo = _50ScreenPixel
		while(true) {
			yFrom += yGraduationLength
			if(yFrom<yTo) break;
			ctx.beginPath()
			ctx.moveTo(x,yFrom)
			ctx.lineTo(x+2*MAPS_AXIS_TICK_SIZE,yFrom)
			ctx.stroke()
			ctx.fillText((i++*MAPS_AXIS_GRADUATION)+'m ',x,yFrom)
		}
	}
}
var computeRequiredAxisSpace = function(c,ctx,ratio) {
	ctx.font = (MAPS_AXIS_FONT_SIZE*ratio)+'px '+MAPS_AXIS_FONT_TYPE
	return ctx.measureText('0000m ').width
}
var toPixel = function(aNormPt,size) {
	return {
		x: 0.5*(aNormPt[0]+1)*size.width,
		y: 0.5*(1-aNormPt[1])*size.height
	}
}
var displayRealPosition = function(e) {
	if(typeof(shorelineTopLeftPoint)==='undefined')
		return true;
		
	var o = $(e.target).offset();
	
	var posInImage = {
			x: ((e.pageX-o.left)*screenRatio)-MAPS_AXIS_SIZE,
			y: (e.pageY-o.top)*screenRatio
	}
	var realCoord = {
		x: shorelineTopLeftPoint.x+(posInImage.x*shorelinePixelLength),
		y: shorelineTopLeftPoint.y+(posInImage.y*shorelinePixelHeight)
	}
	$xCoord.html(realCoord.x.toFixed(5))
	$yCoord.html(realCoord.y.toFixed(5))
	return true;
}
var clearRealPosition = function(e) {
	$xCoord.html('')
	$yCoord.html('')
}
var exportShorelines = function() {
	var fileTitle = 'shorelines.png'
	if('toBlob' in canvas) {
		var w = canvas.width
		var h = canvas.height

		$li = $('#mapsReport_shoreline>ul>li>input[type=checkbox]:checked')
		var fontSize = parseInt(13*w/canvas.clientWidth)
		var lineHeight = 1.2*fontSize
		var nbColumns = 4
		var legendLines = Math.ceil($li.length/nbColumns)
		var legendHeight = (legendLines+2)*lineHeight
		var colWidth = w/nbColumns

		var $tmpCanvas = $('<canvas/>')
			.attr('width',w)
			.attr('height',h+legendHeight)
		var tmpCanvas = $tmpCanvas[0]
		var ctx = tmpCanvas.getContext("2d")
		ctx.fillStyle = 'white';
		ctx.fillRect(0,0,w,h+legendHeight);
		ctx.drawImage(canvas,0,legendHeight);
		
		ctx.font = fontSize+"px Arial";
		ctx.fillStyle = 'black';
		ctx.textBaseline = 'hanging';
		var items = 0
		$.each($li, function(id,item) {
			var date = $(item).attr('id');
			var xOffset = (items%nbColumns)*colWidth
			var yOffset = (parseInt(items/nbColumns)+1)*lineHeight
			var color = shorelines[date]['color']
			ctx.fillStyle = color
			ctx.fillRect(xOffset,yOffset,2*fontSize,fontSize)
			ctx.fillStyle = 'black'
			ctx.fillText(date,xOffset+2.5*fontSize,yOffset);
			items++;
		})

		var fileTitle = 'chart.png'
		tmpCanvas.toBlob(function(imgData) {
			saveAs(imgData,fileTitle)
		},'image/png', 1)
	}
}
var updateTransectsLegend = function(site,camera) {
	$legend = $('#transectReport_transect')	
	$legend.empty();
	$selector = $('<select>')
		.on('change',function(e) {
				transects[site][camera].selected = $(this).val()
				drawTransects(site,camera)
		})
	var i = 1;
	$.each(transects[site][camera]['coord'], function(idx, coord) {
		$('<option/>')
			.attr('value',i)
			.append('Transect '+(i++))
			.appendTo($selector)
		//console.log(coord.from[0]+','+coord.from[1]+' -> '+coord.to[0]+','+coord.to[1])
	})
	$selector.appendTo($legend)
	drawTransects(site,camera)
	
	$('<br/>')
		.appendTo($legend);
		
	$('<button>')
		.append('Plot')
		.addClass('right')
		.attr('id','transectReport_plot')
		.button()
		.on('click',function() {
			$('#chart').hide();
			$(this).button( "option", "disabled", true )
			$('#transectReport_transect>select').prop("disabled", true )
			loadRawShorelines();
		})
		.appendTo($legend)
}
var drawTransects = function(site,camera) {
	var canvas = $transectCanvas[0]
	canvasWidth = canvas.width
	canvasHeight = canvas.height
	var ctx = canvas.getContext('2d');
	ctx.clearRect(0, 0, canvasWidth, canvasHeight)
	ctx.drawImage(transectImg, 0, 0)
	ctx.strokeStyle = TRANSECT_COLOR
	ctx.lineWidth = TRANSECT_LINE_WIDTH
	var i = 1
	var itemchecked = transects[site][camera].selected
	var canvasSize = {
		width: canvasWidth,
		height: canvasHeight
	}
	$.each(transects[site][camera]['coord'], function(idx, coord) {
		ctx.strokeStyle = i==itemchecked ? TRANSECT_SELECTED_COLOR : TRANSECT_COLOR
		ctx.beginPath()
		var from = toPixel(coord.from,canvasSize)
		ctx.moveTo(from.x,from.y)
		var to = toPixel(coord.to,canvasSize)
		ctx.lineTo(to.x,to.y)
		ctx.stroke()
		i++
	})
}
var loadTransects = function() {
	$.each(sitesDesc,function(siteName,desc) {
		transects[siteName] = {}
		$.each(desc.cameras,function(idx,cameraName) {
			if(!transects[siteName][cameraName])
				transects[siteName][cameraName] = {
					coord: [],
					selected: 1
				}
			var transectPath = buildResPath({
					'site': siteName,
					'camera': cameraName,
					'prefix': 'static',
					'type': 'transect',
					'ext': 'json'
				})
				
			$.getJSON(transectPath, function( data ) {
				transects[siteName][cameraName]['coord'] = data
				createDownloadLink(siteName,cameraName);
				console.log("Have "+data.length+" transects for "+siteName+" "+cameraName)
			}).fail(function() {
				console.log("Error downloading transects for "+siteName+" "+cameraName)	
			})
		})
	})
}
var wcsShoreline;
var shorelineIntersections = []
var selectedTransect = {};
var distanceSeries = [];
var averagedDistanceSeries = [];
var minDistanceSeries = [];
var maxDistanceSeries = [];
var yAxisMin = null;
var yAxisMax = null;
var loadRawShorelines = function() {
	wcsShoreline = {}
	shorelineIntersections = []
	selectedTransect = {}
	distanceSeries = []
	averagedDistanceSeries = []
	minDistanceSeries = []
	maxDistanceSeries = []
	yAxisMin = yAxisMax = null;
	var siteName = $('#transectReport_sites > input[name=transectReport_sites]:checked').val()
	var cameraName = $('#transectReport_cameras').val()
	var transectId = $('#transectReport_transect>select').val()-1
	selectedTransect = {
		from: transects[siteName][cameraName]['coord'][transectId]['wcsFrom'],
		to: transects[siteName][cameraName]['coord'][transectId]['wcsTo'],
	}
	
	var _30Min=30*60*1000
	var fromDateStr = $('#transectReport_from').val()
	var fromDate = new Date(fromDateStr)
	var from = new Date(_30Min*(fromDate.getTime()/_30Min).toFixed(0))
	
	var endDateStr = $('#transectReport_to').val()
	var endDate = new Date(endDateStr)
	var end = new Date(_30Min*(endDate.getTime()/_30Min).toFixed(0))
	
	/// Load shorelines for all dates	
	var _30Min = 30*60*1000
	var files = []
	while(true) {
		var dir = createDirectoryFromDate(from);
		var shorelineFileName = buildResPath({
			'site': siteName,
			'camera': cameraName,
			'prefix': dir,
			'type': 'wcs_shoreline',
			'ext': 'json'
		});	

		files.push({
			date: from,
			path: shorelineFileName
		});
		
		from = new Date(from.getTime()+_30Min)
		if(from.getTime()>end.getTime()) break;
	}

	var filesDownloaded = 0;
	var filesToDownload = files.length;
	$progressLabel.text('Loading '+filesDownloaded+'/'+filesToDownload)
	$progressbar.progressbar('value',false);
	$progressbar.show();
	$('#transectReport_buttonsBar').hide();
	
	(function downloadWorker(index) {
		var file = files[index];
		$.getJSON(file.path, function( data ) {
			filesDownloaded++
			$progressLabel.text('Loading '+filesDownloaded+'/'+filesToDownload)
			wcsShoreline[file.date] = data.wcsShoreline
			if(filesToDownload==filesDownloaded) return processShorelines()
			
		}).fail(function() {
			filesDownloaded++
			$progressLabel.text('Loading '+filesDownloaded+'/'+filesToDownload)
			if(filesToDownload==filesDownloaded) return processShorelines()
		})

		if(index==files.length-1) return;
		setTimeout(function() { downloadWorker(++index) },16)
	})(0)
}			
var processShorelines = function() {
	
	(function Worker(index) {
		
		var shorelinesCount = Object.keys(wcsShoreline).length
		if(index==shorelinesCount) 
			return drawShorelineChart();
			
		var date = Object.keys(wcsShoreline)[index]
		var shoreline = wcsShoreline[date]
		computeShorelineIntersection(new Date(date),shoreline);
		
		var task = Math.floor(100*(index+1)/shorelinesCount)
		$progressbar.progressbar('value', task);
		setTimeout(function() { Worker(++index) },10)
	})(0)
}
var computeShorelineIntersection = function(date,shoreline) {
	
	var minDist = null;
	for(var i=0;i<shoreline.length-1;i++) {
		var sh1 = {x: shoreline[i][0], y: shoreline[i][1]}
		var sh2 = {x: shoreline[i+1][0], y: shoreline[i+1][1]}
		var tr1 = {x: selectedTransect['from'][0], y: selectedTransect['from'][1]}
		var tr2 = {x: selectedTransect['to'][0], y: selectedTransect['to'][1]}
		
		/// Compute intersection
		var intersection = doLineSegmentsIntersect(sh1, sh2, tr1, tr2)
		
		if(false!==intersection) {
			/// Compute distance 
			var dist = Math.sqrt(Math.pow(intersection.x-tr1.x,2)+Math.pow(intersection.y-tr1.y,2))
			
			/// Update minimum distance	
			if(minDist==null || minDist<dist)
				minDist = dist;
		}
	}
	
	if(minDist!=null) {
		distanceSeries.push([date,minDist])
		if(yAxisMin==null || minDist<yAxisMin) yAxisMin=minDist
		if(yAxisMax==null || minDist>yAxisMax) yAxisMax=minDist
	}	
}

function doLineSegmentsIntersect(p, p2, q, q2) {
	
	var r = subtractPoints(p2, p);
	var s = subtractPoints(q2, q);

	var uNumerator = crossProduct(subtractPoints(q, p), r);
	var denominator = crossProduct(r, s);

	if (uNumerator == 0 && denominator == 0) {
		// They are coLlinear
		return false
		
		/*
		// Do they touch? (Are any of the points equal?)
		if (equalPoints(p, q) || equalPoints(p, q2) || equalPoints(p2, q) || equalPoints(p2, q2)) {
			return true
		}
		// Do they overlap? (Are all the point differences in either direction the same sign)
		// Using != as exclusive or
		return ((q.x - p.x < 0) != (q.x - p2.x < 0) != (q2.x - p.x < 0) != (q2.x - p2.x < 0)) || 
			((q.y - p.y < 0) != (q.y - p2.y < 0) != (q2.y - p.y < 0) != (q2.y - p2.y < 0))
		*/
	}

	if (denominator == 0) {
		// lines are paralell
		return false
	}

	var u = uNumerator / denominator;
	var t = crossProduct(subtractPoints(q, p), s) / denominator;

	if((t >= 0) && (t <= 1) && (u >= 0) && (u <= 1)) {
		// p + t r = q + u s.
		return {
			x: p.x + t * r.x,
			y: p.y + t * r.y
		}
	}
	return false;
}

/**
 * Calculate the cross product of the two points.
 * 
 * @param {Object} point1 point object with x and y coordinates
 * @param {Object} point2 point object with x and y coordinates
 * 
 * @return the cross product result as a float
 */
function crossProduct(point1, point2) {
	return point1.x * point2.y - point1.y * point2.x;
}

/**
 * Subtract the second point from the first.
 * 
 * @param {Object} point1 point object with x and y coordinates
 * @param {Object} point2 point object with x and y coordinates
 * 
 * @return the subtraction result as a point object
 */ 
function subtractPoints(point1, point2) {
	var result = {};
	result.x = point1.x - point2.x;
	result.y = point1.y - point2.y;

	return result;
}

/**
 * See if the points are equal.
 *
 * @param {Object} point1 point object with x and y coordinates
 * @param {Object} point2 point object with x and y coordinates
 *
 * @return if the points are equal
 */
function equalPoints(point1, point2) {
	return (point1.x == point2.x) && (point1.y == point2.y)
}
var chartOpt = {
	canvas: true,
	xaxis : {
		mode: 'time',
		timezone: "browser",
		labelHeight: 40
	},
	yaxis : {
		labelWidth: 40,
		min: null,
		max: null
	},
	points: {
		show: true
	},
	lines: {
		show: true,
		fill: true
	},
	legend : {
		show: true,
		noColumns: 1,
		backgroundColor: '#fff',
		backgroundOpacity: 0.75,
		margin: 5
	},
	selection: {
		mode: "x"
	},
	grid: {
		backgroundColor: { colors: [ "#fff", "#eee" ] },
		margin: {
			left: 20
		},
		hoverable: true,
		markingsLineWidth: 2
	}
}
var $chart;
var drawShorelineChart = function() {
	$('#chart').show()
	$('#transectReport_chartModeRaw').prop('checked','checked')
	$('#transectReport_chartMode').buttonset('refresh')
	$('#transectReport_buttonsBar').show()
	$('#transectReport_plot').button( "option", "disabled", false )
	$('#transectReport_transect>select').prop("disabled", false )
	$progressbar.hide()
	

	$placeholder = $('#placeholder')
	$placeholder.unbind("plotselected")
	$placeholder.bind("plotselected", function (event, ranges) {
		console.log("plot selected ...");
		$.each($chart.getXAxes(), function(_, axis) {
			var opts = axis.options
			opts.min = ranges.xaxis.from
			opts.max = ranges.xaxis.to
		});
		$chart.setupGrid()
		$chart.draw()
		$chart.clearSelection()
	});
	/// Reindex distances series
	distanceSeries.sort(function(a,b) {
		return a[0].getTime()-b[0].getTime()
	})
	createFlotChart($placeholder,[distanceSeries],chartOpt)
}
var createFlotChart = function($placeholder,series,opt) {
	opt.yaxis.min = yAxisMin-TRANSECT_Y_AXIS_EPSILON
	opt.yaxis.max = yAxisMax+TRANSECT_Y_AXIS_EPSILON
	$chart = $.plot($placeholder,series,opt)
	var xaxisLabel = $("<div class='axisLabel xaxisLabel'></div>")
	  .text("Time")
	  .appendTo($('#placeholder'));

	var yaxisLabel = $("<div class='axisLabel yaxisLabel'></div>")
	  .text("Shoreline distance (Meter)")
	  .appendTo($('#placeholder'));
	yaxisLabel.css("margin-top", yaxisLabel.width() / 2 - 20); 	
}
var clearZoom = function() {
	var axes = $chart.getAxes();
	var isRawMode = $('#transectReport_chartModeRaw').is(':checked')
	var from = isRawMode ? distanceSeries[0][0] : averagedDistanceSeries[0][0]
	var to = isRawMode ? distanceSeries[distanceSeries.length-1][0] : averagedDistanceSeries[averagedDistanceSeries.length-1][0]
	$chart.setSelection({	xaxis: {
						from: from,
						to: to
					}
				})
}
var _Day = 24*60*60*1000
var updateChartMode = function() {
	
	if(!averagedDistanceSeries.length) {
		///	Compute the average per day
		var perDayDist = {}
		$.each(distanceSeries, function(idx,val) {
			/// Round date to align it to the day start
			var d = new Date(parseInt(val[0].getTime()/_Day)*_Day)
			if(typeof(perDayDist[d])==='undefined')
				perDayDist[d] = []

			perDayDist[d].push(val[1])	
		})
		
		$.each(perDayDist, function(date,valList) { 
			var sum = 0
			var min, max
			$.each(valList, function(idx,rawVal) { 
				sum += rawVal
				if(idx==0) min=max=rawVal
				else if(rawVal<min) min=rawVal
				else if(rawVal>max) max=rawVal
			})
			averagedDistanceSeries.push([new Date(date),sum/valList.length])
			minDistanceSeries.push([new Date(date),min])
			maxDistanceSeries.push([new Date(date),max])
		})
	}
	var series;
	if($('#transectReport_chartModeRaw').is(':checked'))
		series = [distanceSeries]
	else
		series = [	
			{label: 'Averaged', data: averagedDistanceSeries},
			{label: 'Minimum',  data: minDistanceSeries},
			{label: 'Maximum',  data: maxDistanceSeries} 
		]
		
	createFlotChart($placeholder,series,chartOpt)	
}
var exportChart = function() {
	var canvas = $chart.getCanvas();
	if('toBlob' in canvas) {
		var w = canvas.width
		var h = canvas.height

		var $tmpCanvas = $('<canvas/>')
			.attr('width',w)
			.attr('height',h)
		var tmpCanvas = $tmpCanvas[0]
		var ctx = tmpCanvas.getContext("2d")
		ctx.fillStyle = 'white';
		ctx.fillRect(0,0,w,h);
		ctx.drawImage(canvas,0,0);
		var fileTitle = 'shoreline.png'
		tmpCanvas.toBlob(function(imgData) {
			saveAs(imgData,fileTitle)
		},'image/png', 1)					
	}
}
var exportCsv = function() {
	var fileTitle = 'shoreline.csv'
	var csvContent = '#Time,Value\n'
	var series = $('#transectReport_chartModeRaw').is(':checked') ? distanceSeries : averagedDistanceSeries
	$.each(series, function(idx,pt) {
		var date = new Date()
		date.setTime(pt[0].getTime())
		csvContent += new Date(date)+','+pt[1]+'\n'
	})
	var data = new Blob([csvContent], {type: 'text/csv;charset=UTF-8'});
	saveAs(data,fileTitle)
}
var createDownloadLink = function(siteName,cameraName) {
	
	$dlt = $('#download_transect');
	for(var i=1;i<=transects[siteName][cameraName]['coord'].length;i++) {
		var resDesc = {
			prefix: 'static',
			site: siteName,
			camera: cameraName,
			type: 'transect_'+i,
		}
		$li = $('<li/>')
			.append(siteName+' '+cameraName+' transect '+i)
		
		resDesc.ext = 'shp'	
		$('<a/>')
			.attr('href',buildResPath(resDesc))
			.append('shp')
			.appendTo($li)
			
		resDesc.ext = 'shx'	
		$('<a/>')
			.attr('href',buildResPath(resDesc))
			.append('shx')
			.appendTo($li)

		resDesc.ext = 'dbf'	
		$('<a/>')
			.attr('href',buildResPath(resDesc))
			.append('dbf')
			.appendTo($li)

		resDesc.ext = 'prj'	
		$('<a/>')
			.attr('href',buildResPath(resDesc))
			.append('prj')
			.appendTo($li)

		$li.appendTo($dlt)
	}
	$dlt.append('<br/>');
}
var downloadArchive = function() {
	var sites = $('#download_sites > input[type=checkbox]:checked').length;
	var $siteLabel = $('#download_site_label');
	if(!sites) {
		$siteLabel.addClass('error');
		return false;
	}
	$siteLabel.removeClass('error');
	
	var files = $('#download_files > input[type=checkbox]:checked').length;
	var $fileLabel = $('#download_files_label');
	if(!files) {
		$fileLabel.addClass('error');
		return false;
	}
	$fileLabel.removeClass('error');
	
	var _30Min=30*60*1000
	var fromDateStr = $('#download_from').val()
	if(fromDateStr=='') {
		$('#download_from_label').addClass('error');
		return false;
	}
	$('#download_from_label').removeClass('error');
	var fromDate = new Date(fromDateStr)
	var from = new Date(_30Min*(fromDate.getTime()/_30Min).toFixed(0))
	var endDateStr = $('#download_to').val()
	if(endDateStr=='') {
		$('#download_to_label').addClass('error');
		return false;
	}
	$('#download_to_label').removeClass('error');
	var endDate = new Date(endDateStr)
	var end = new Date(_30Min*(endDate.getTime()/_30Min).toFixed(0))	
	if(end.getTime()<=from.getTime()) {
		$('#download_from_label').addClass('error');
		return false;
	}
	$('#download_from_label').removeClass('error');

	return true;
}
$(document).ready(function() {

	$('#tabs').tabs()
	$('#imagesReport_table').hide()
	
	///currentImages
	var i=1;
	$imagesList = $('#currentImages_list');
	$.each(sitesDesc,function(siteName,desc) {
		$.each(desc.cameras,function(idx,cameraName) {
			if(cameraName == 'merged rectified') return true;
			$img = $('<div/>')
				.attr('class','grid_6')
				.append(
					$('<label/>')
						.append(siteName+' '+cameraName)
				)
				.append(createImageTypeSelector(siteName,cameraName))
				.append(
					$('<div class="imgHolder">')
						.append(
							$('<img/>')
								.attr('width','460')
								.attr('height','')
								.attr('src', buildResPath({
										'prefix': 'current',
										'site': siteName,
										'camera': cameraName,
										'type': 'snapshot'
									})
								)
						)
				)
				.appendTo($imagesList);
				
			if(!(i%2)) {
				$('<div>')
					.attr('class','clear bigSpacer')
					.appendTo($imagesList);
			}
			i++;
			
		});
			
	});
	
	///imagesReport
	$imagesSites = $('#imagesReport_sites');
	$.each(sitesDesc,function(siteName,site) {
		var siteId = 'site_'+siteName.replace(/ /g,'_');
		$label = $('<label/>').
			attr('for',siteId).
			append(siteName);
		$input = $('<input/>')
			.attr('type','radio')
			.attr('id',siteId)
			.attr('name','imagesReport_sites')
			.attr('value',siteName)
		$input.appendTo($imagesSites)
		$label.appendTo($imagesSites)
	})
	$('#imagesReport_sites').buttonset()
	var now = new Date()
	var $fromDP = $('#imagesReport_from').datetimepicker({ dateFormat: 'yy/mm/dd', timeFormat: 'HH:mm:00' })
	$fromDP.datetimepicker('setDate', new Date(now.getTime()-24*60*60*1000))
	var $toDP = $('#imagesReport_to').datetimepicker({ dateFormat: 'yy/mm/dd', timeFormat: 'HH:mm:00' })
	$toDP.datetimepicker('setDate', now)
	$('#imagesReport_interval').buttonset()
	$('#imagesReport_types').buttonset()
	$('#imagesReport_request')
		.button()
		.on('click',function() {
			loadImageReport()
		});
	
	$('#image_dialog').dialog({
			autoOpen: false,
			width: $(window).width(),
			height: $(window).height(),
			left: '0px',
			top:'0px',
			buttons: [{
				text: "Download",
				click: function() {
					var $img = $(this).children('img')
					var img = $img[0];
					var canvas = document.createElement('canvas');
					canvas.width = img.width;
					canvas.height = img.height;
					var context = canvas.getContext('2d');
					
					context.drawImage(img, 0, 0 );

					var fileTitle = img.dataset.title.replace(/ /g,'_')+'.jpeg'
					if('toBlob' in canvas) {
						canvas.toBlob(function(imgData) {
							saveAs(imgData,fileTitle)
						},'image/jpeg', 1)	
					}
					
				}
			}]
	})
	
	///maps
	$mapsSites = $('#mapsReport_sites');
	$.each(sitesDesc,function(siteName,site) {
		var siteId = 'mapsReport_site_'+siteName.replace(/ /g,'_')
		$label = $('<label/>').
			attr('for',siteId).
			append(siteName);
		$input = $('<input/>')
			.attr('type','radio')
			.attr('id',siteId)
			.attr('name','mapsReport_sites')
			.attr('value',siteName)
			.on('change',function() {
				$camerasList = $('#mapsReport_cameras')
				$("#mapsReport_cameras option[value!='']").remove();
				$.each(sitesDesc[this.value].cameras,function(idx,name) {
					$('<option/>')
						.attr('value',name)
						.append(name)
						.appendTo($camerasList)
				})
			})
		$input.appendTo($mapsSites)
		$label.appendTo($mapsSites)
	})
	$('#mapsReport_sites').buttonset()
	var now = new Date()
	$fromDP = $('#mapsReport_from').datetimepicker({ dateFormat: 'yy/mm/dd', timeFormat: 'HH:mm:00' })
	$fromDP.datetimepicker('setDate', new Date(now.getTime()-24*60*60*1000))
	$toDP = $('#mapsReport_to').datetimepicker({ dateFormat: 'yy/mm/dd', timeFormat: 'HH:mm:00' })
	$toDP.datetimepicker('setDate', now)
	$('#mapsReport_request')
		.button()
		.on('click',function() {
			loadMapsReport()
		});
	$mapsCanvas = $('#mapsReport_canvas')
	$mapsCanvas.hide()
	$mapsCanvas
		.on('mousemove', function(e) {
			displayRealPosition(e);
		})
		.on('mouseout', function(e) {
			clearRealPosition(e);
		});
	$xCoord = $('#xcoord')
	$yCoord = $('#ycoord')
	$coordArea = $('#coordArea')
	$coordArea.hide()
	
	///transect
	$transectSites = $('#transectReport_sites');
	$.each(sitesDesc,function(siteName,site) {
		var siteId = '#transectReport_site_'+siteName.replace(/ /g,'_')
		$label = $('<label/>').
			attr('for',siteId).
			append(siteName);
		$input = $('<input/>')
			.attr('type','radio')
			.attr('id',siteId)
			.attr('name','transectReport_sites')
			.attr('value',siteName)
			.on('change',function() {
				$camerasList = $('#transectReport_cameras')
				$("#transectReport_cameras option[value!='']").remove();
				$.each(sitesDesc[this.value].cameras,function(idx,name) {
					if(name == 'merged rectified') return true;
					$('<option/>')
						.attr('value',name)
						.append(name)
						.appendTo($camerasList)
				})
			})
		$input.appendTo($transectSites)
		$label.appendTo($transectSites)
	})
	$('#transectReport_sites').buttonset()
	var now = new Date()
	$fromDP = $('#transectReport_from').datetimepicker({ dateFormat: 'yy/mm/dd', timeFormat: 'HH:mm:00' })
	$fromDP.datetimepicker('setDate', new Date(now.getTime()-24*60*60*1000))
	$toDP = $('#transectReport_to').datetimepicker({ dateFormat: 'yy/mm/dd', timeFormat: 'HH:mm:00' })
	$toDP.datetimepicker('setDate', now)
	$('#transectReport_request')
		.button()
		.on('click',function() {
			loadTransectsReport()
		});
	$transectCanvas = $('#transectReport_canvas')
	$transectCanvas.hide()
				
	$progressbar = $( "#progressbar" ),
	$progressLabel = $( ".progress-label" );
	$progressbar.progressbar({
		value: false,
		change: function() {
			var val = $progressbar.progressbar( "value" )
			if(typeof(val)==="number") $progressLabel.text( val + "%" );
			else if(typeof(val)==="string") $progressLabel.text( val );
			else $progressLabel.text( "Loading..." );
		}
	});
	$progressbar.hide();
	loadTransects();
	$("<div id='tooltip'/>").css({
		'position': 'absolute',
		'display': 'none',
		'border': '1px solid #fdd',
		'padding': '4px',
		'background-color': '#fee',
		'opacity': 0.8,
		'border-radius': '5px'
	}).appendTo("body");
	
	$("#placeholder").bind("plothover", function (event, pos, item) {
		if (item) {
			var x = item.datapoint[0],
				y = item.datapoint[1].toFixed(3);
			
			var cssProp = {top: item.pageY+5, left: item.pageX+5, right: 'auto'}
			$("#tooltip").html(y+" m<br>"+new Date(x))
				.css(cssProp)
				.fadeIn(200);
		} else {
			$("#tooltip").hide();
		}
	});
	$buttonsBar = $('#transectReport_buttonsBar')
	$('<button/>')
		.append('Clear zoom')
		.button()
		.appendTo($buttonsBar)
		.on('click', function() { clearZoom() })
		
	$buttonsBar.append('&nbsp;')
		
	var chartMode = {
		'Raw' : 'Raw values',
		'Averaged' : 'Averaged per days'
	}
	$chartMode = $('<div></div>')
					.attr('id','transectReport_chartMode')
					.css('display','inline-block')
	$.each(chartMode, function(id,label) {
		$input = $('<input/>')
			.attr('type','radio')
			.attr('id','transectReport_chartMode'+id)
			.attr('name','transectReport_chartMode')
			.attr('value',label)
			.prop('checked',(id=='Raw')?'checked':null)
			.on('change',function() { updateChartMode() })

		$input.appendTo($chartMode)	
		$('<label/>')
			.attr('for','transectReport_chartMode'+id)
			.append(label)
			.appendTo($chartMode)
		
	})	
	
	$chartMode.buttonset()
	$chartMode.appendTo($buttonsBar)
	
	$buttonsBar.append('&nbsp;')
	
	$('<button/>')
		.append('Export chart')
		.button()
		.appendTo($buttonsBar)
		.on('click', function() { exportChart() })
		
	$('<button/>')
		.append('Export csv')
		.button()
		.appendTo($buttonsBar)
		.on('click', function() { exportCsv() })
		
	$buttonsBar.hide()
	
	/// Download
	$('#download_request')
		.button()
		.on('click',function() {
			return downloadArchive()	
		});
	$fromDP = $('#download_from').datetimepicker({ dateFormat: 'yy/mm/dd', timeFormat: 'HH:mm:00' })
	$fromDP.datetimepicker('setDate', new Date(now.getTime()-24*60*60*1000))
	$toDP = $('#download_to').datetimepicker({ dateFormat: 'yy/mm/dd', timeFormat: 'HH:mm:00' })
	$toDP.datetimepicker('setDate', now)
	$('#download_files').buttonset();
	$dlSites = $('#download_sites');
	$.each(sitesDesc,function(siteName,site) {
		$.each(site.cameras,function(idx,name) {
			id = siteName.toLowerCase().replace(/ /g,'_')+'_'+name.toLowerCase().replace(/ /g,'_')
			$input = $('<input/>')
					.attr('id',id)
					.attr('type','checkbox')
					.attr('name','prefix[]')
					.attr('value',id)
					.appendTo($dlSites)
			$('<label>')
					.attr('for',id)
					.append(siteName+' '+name)
					.appendTo($dlSites)
		})
	})
	$dlSites.buttonset();
});
