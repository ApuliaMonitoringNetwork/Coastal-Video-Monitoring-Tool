/*====================================================================
| Version: May 14, 2012
\===================================================================*/

/*====================================================================
| Philippe Thevenaz
| EPFL/STI/IMT/LIB/BM4137
| Station 17
| CH-1015 Lausanne VD
| Switzerland
|
| phone (CET): +41(21)693.51.61
| fax: +41(21)693.68.10
| RFC-822: philippe.thevenaz@epfl.ch
| X-400: /C=ch/A=400net/P=switch/O=epfl/S=thevenaz/G=philippe/
| URL: http://bigwww.epfl.ch/
\===================================================================*/

/*====================================================================
| This work is based on the following paper:
|
| P. Thevenaz, D. Sage, M. Unser
| Bi-Exponential Edge-Preserving Smoother
| IEEE Transactions on Image Processing, in press
|
| Other relevant on-line publications are available at
| http://bigwww.epfl.ch/publications/
\===================================================================*/

/*====================================================================
| Additional help available at http://bigwww.epfl.ch/
|
| You'll be free to use this software for research purposes, but you
| should not redistribute it without our consent. In addition, we
| expect you to include a citation or acknowledgment whenever you
| present or publish results that are based on it. EPFL makes no
| warranties of any kind on this software and shall in no event be
| liable for damages of any kind in connection with the use and
| exploitation of this technology.
\===================================================================*/

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.gui.GUI;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.Recorder;
import ij.process.ImageProcessor;
import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.FlowLayout;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;

import static java.lang.Math.PI;
import static java.lang.Math.cosh;
import static java.lang.Math.exp;
import static java.lang.Math.pow;
import static java.lang.Math.tanh;

/*====================================================================
|	BEEPS_
\===================================================================*/
/*------------------------------------------------------------------*/
public class BEEPS_
	implements
		ExtendedPlugInFilter

{ /* begin class BEEPS_ */

/*....................................................................
	protected variables
....................................................................*/
/*------------------------------------------------------------------*/
protected static final String ITERATIONS =
	"Iterations";

/*------------------------------------------------------------------*/
protected static final String RANGE_FILTER =
	"Range_Filter";

/*------------------------------------------------------------------*/
protected static final String SPATIAL_DECAY =
	"Spatial_Decay";

/*------------------------------------------------------------------*/
protected static final String PHOTOMETRIC_STANDARD_DEVIATION =
	"Photometric_Standard_Deviation";

/*------------------------------------------------------------------*/
protected static final String[] RANGE_FILTERS = {
	"gauss",
	"sech" //,
//	"tanh",
//	"oneSided"
};

/*------------------------------------------------------------------*/
protected static final double ZETA_3 =
	1.2020569031595942853997381615114499907649862923404988817922715553418382057;

/*....................................................................
	private variables
....................................................................*/
private final GenericDialog dialog = new GenericDialog("BEEPS");
private static double spatialDecay = 0.01;
private static double photometricStandardDeviation = 30.0;
private static final int CAPABILITIES = DOES_8G | DOES_16 | DOES_32
	| CONVERT_TO_FLOAT | DOES_STACKS;
private static int iterations = 1;
private static int rangeFilter = 0;

/*....................................................................
	ExtendedPlugInFilter methods
....................................................................*/
/*------------------------------------------------------------------*/
public void run (
	final ImageProcessor ip
) {
	final Vector<?> numbers = dialog.getNumericFields();
	photometricStandardDeviation =
		(new Double(((TextField)numbers.elementAt(0)).getText())).doubleValue();
	spatialDecay =
		(new Double(((TextField)numbers.elementAt(1)).getText())).doubleValue();
	iterations =
		(new Integer(((TextField)numbers.elementAt(2)).getText())).intValue();
	final Vector<?> choices = dialog.getChoices();
	rangeFilter = ((Choice)choices.elementAt(0)).getSelectedIndex();
	if (rangeFilter < 0) {
		IJ.error("Internal error: unexpected type of range filter");
		return;
	}
	if (!dialog.getPreviewCheckbox().getState()) {
		Recorder.setCommand("BEEPS ");
		Recorder.recordOption(RANGE_FILTER,
			((Choice)choices.elementAt(0)).getSelectedItem());
		Recorder.recordOption(PHOTOMETRIC_STANDARD_DEVIATION,
			"" + photometricStandardDeviation);
		Recorder.recordOption(SPATIAL_DECAY,
			"" + spatialDecay);
		Recorder.recordOption(ITERATIONS,
			"" + iterations);
		Recorder.saveCommand();
	}
	final int width = ip.getWidth();
	final int height = ip.getHeight();
	final float[] data = (float[])ip.getPixels();
	for (int i = 0; (i < iterations); i++) {
		float[] duplicate = Arrays.copyOf(data, data.length);
		Thread h = new Thread(new BEEPSHorizontalVertical(duplicate, width,
			height, rangeFilter, photometricStandardDeviation, spatialDecay));
		Thread v = new Thread(new BEEPSVerticalHorizontal(data, width, height,
			rangeFilter, photometricStandardDeviation, spatialDecay));
		h.start();
		v.start();
		try {
			h.join();
			v.join();
		}
		catch (InterruptedException e) {
		}
		for (int k = 0, K = data.length; (k < K); k++) {
			data[k] = 0.5F * (data[k] + duplicate[k]);
		}
	}
} /* end run */

/*------------------------------------------------------------------*/
public void setNPasses (
	final int nPasses
) {
} /* end setNPasses */

/*------------------------------------------------------------------*/
public int setup (
	final String arg,
	final ImagePlus imp
) {
	return(CAPABILITIES);
} /* end setup */

/*------------------------------------------------------------------*/
public int showDialog (
	final ImagePlus imp,
	final String command,
	final PlugInFilterRunner pfr
) {
	dialog.addChoice(RANGE_FILTER, RANGE_FILTERS,
		RANGE_FILTERS[rangeFilter]);
	dialog.addNumericField(PHOTOMETRIC_STANDARD_DEVIATION,
		photometricStandardDeviation, 4);
	dialog.addNumericField(SPATIAL_DECAY,
		spatialDecay, 4);
	dialog.addNumericField(ITERATIONS,
		iterations, 0);
	dialog.addPreviewCheckbox(pfr);
	dialog.addPanel(new BEEPSCreditsButton());
	if (Macro.getOptions() != null) {
		activateMacro(imp);
		return(CAPABILITIES);
	}
	dialog.showDialog();
	if (dialog.wasCanceled()) {
		return(DONE);
	}
	if (dialog.wasOKed()) {
		dialog.getPreviewCheckbox().setState(false);
		return(CAPABILITIES);
	}
	else {
		return(DONE);
	}
} /* end showDialog */

/*....................................................................
	private methods
....................................................................*/
/*------------------------------------------------------------------*/
private void activateMacro (
	final ImagePlus imp
) {
	final Vector<?> choices = dialog.getChoices();
	final Vector<?> numbers = dialog.getNumericFields();
	final Choice rangeFilter = (Choice)choices.elementAt(0);
	final TextField photometricStandardDeviation =
		(TextField)numbers.elementAt(0);
	final TextField spatialDecay =
		(TextField)numbers.elementAt(1);
	final TextField iterations =
		(TextField)numbers.elementAt(2);
	final String options = Macro.getOptions();
	rangeFilter.select(Macro.getValue(options,
		RANGE_FILTER, "" + RANGE_FILTERS[0]));
	photometricStandardDeviation.setText(Macro.getValue(options,
		PHOTOMETRIC_STANDARD_DEVIATION, "" + 30.0));
	spatialDecay.setText(Macro.getValue(options,
		SPATIAL_DECAY, "" + 0.01));
	iterations.setText(Macro.getValue(options,
		ITERATIONS, "" + 1));
} /* end activateMacro */

} /* end class BEEPS_ */

/*====================================================================
|	BEEPSCredits
\===================================================================*/
/*------------------------------------------------------------------*/
class BEEPSCredits
	extends
		JDialog
	implements
		ActionListener

{ /* begin class BEEPSCredits */

/*....................................................................
	private variables
....................................................................*/
private static final long serialVersionUID = 1L;

/*....................................................................
	BEEPSCredits constructors
....................................................................*/
/*------------------------------------------------------------------*/
protected BEEPSCredits (
	final JFrame parentWindow
) {
	super(parentWindow, "BEEPS", true);
	getContentPane().setLayout(new BorderLayout(0, 20));
	final JPanel buttonPanel = new JPanel();
	buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
	final JButton doneButton = new JButton("Done");
	doneButton.addActionListener(this);
	buttonPanel.add(doneButton);
	final TextArea text = new TextArea(23, 56);
	text.setEditable(false);
	text.append(
		"\n");
	text.append(
		" This work is based on the following paper:\n");
	text.append(
		"\n");
	text.append(
		" P. Th\u00E9venaz, D. Sage, M. Unser\n");
	text.append(
		" Bi-Exponential Edge-Preserving Smoother\n");
	text.append(
		" IEEE Transactions on Image Processing\n");
	text.append(
		" in press\n");
	text.append(
		"\n");
	text.append(
		" Other relevant on-line publications are available at\n");
	text.append(
		" http://bigwww.epfl.ch/publications/\n");
	text.append(
		"\n");
	text.append(
		" Additional help available at\n");
	text.append(
		" http://bigwww.epfl.ch/thevenaz/BEEPS/\n");
	text.append(
		"\n");
	text.append(
		" You'll be free to use this software for research purposes,\n");
	text.append(
		" but you should not redistribute it without our consent. In\n");
	text.append(
		" addition, we expect you to include a citation or an\n");
	text.append(
		" acknowledgment whenever you present or publish results\n");
	text.append(
		" that are based on it.\n");
	text.append(
		"\n");
	text.append(
		" EPFL makes no warranties of any kind on this software and\n");
	text.append(
		" shall in no event be liable for damages of any kind in\n");
	text.append(
		" connection with the use and exploitation of this technology.\n");
	getContentPane().add("Center", text);
	getContentPane().add("South", buttonPanel);
	pack();
	GUI.center(this);
	setVisible(true);
} /* end BEEPSCredits */

/*....................................................................
	ActionListener methods
....................................................................*/
/*------------------------------------------------------------------*/
public void actionPerformed (
	final ActionEvent e
) {
	if (e.getActionCommand().equals("Done")) {
		dispose();
	}
} /* end actionPerformed */

} /* end class BEEPSCredits */

/*====================================================================
|	BEEPSCreditsButton
\===================================================================*/
/*------------------------------------------------------------------*/
class BEEPSCreditsButton
	extends
		Panel
	implements
		ActionListener

{ /* begin class BEEPSCreditsButton */

/*....................................................................
	private variables
....................................................................*/
private static final long serialVersionUID = 1L;

/*....................................................................
	BEEPSCreditsButton constructors
....................................................................*/
/*------------------------------------------------------------------*/
protected BEEPSCreditsButton (
) {
	super();
	final JButton creditsButton = new JButton("Credits");
	creditsButton.addActionListener(this);
	add(creditsButton);
} /* end BEEPSCreditsButton */

/*....................................................................
	ActionListener methods
....................................................................*/
/*------------------------------------------------------------------*/
public void actionPerformed (
	final ActionEvent e
) {
	new BEEPSCredits(null);
} /* end actionPerformed */

} /* end class BEEPSCreditsButton */

/*====================================================================
|	BEEPSGain
\===================================================================*/
/*------------------------------------------------------------------*/
class BEEPSGain
	implements
		Runnable

{ /* begin class BEEPSGain */

/*....................................................................
	private variables
....................................................................*/
private double[] data;
private int length;
private int startIndex;
private static double mu;

/*....................................................................
	BEEPSGain constructors
....................................................................*/
/*------------------------------------------------------------------*/
protected BEEPSGain (
	final double[] data,
	final int startIndex,
	final int length
) {
	this.data = data;
	this.startIndex = startIndex;
	this.length = length;
} /* end BEEPSGain */

/*....................................................................
	static methods
....................................................................*/
/*------------------------------------------------------------------*/
protected static void setup (
	final double spatialContraDecay
) {
	mu = (1.0 - spatialContraDecay) / (1.0 + spatialContraDecay);
} /* end setup */

/*....................................................................
	Runnable methods
....................................................................*/
/*------------------------------------------------------------------*/
public void run (
) {
	for (int k = startIndex, K = startIndex + length; (k < K); k++) {
		data[k] *= mu;
	}
} /* end run */

} /* end class BEEPSGain */

/*====================================================================
|	BEEPSHorizontalVertical
\===================================================================*/
/*------------------------------------------------------------------*/
class BEEPSHorizontalVertical
	implements
		Runnable

{ /* begin class BEEPSHorizontalVertical */

/*....................................................................
	private variables
....................................................................*/
private double photometricStandardDeviation;
private double spatialDecay;
private float[] data;
private int height;
private int rangeFilter;
private int width;

/*....................................................................
	BEEPSHorizontalVertical constructors
....................................................................*/
/*------------------------------------------------------------------*/
protected BEEPSHorizontalVertical (
	final float[] data,
	final int width,
	final int height,
	final int rangeFilter,
	final double photometricStandardDeviation,
	final double spatialDecay
) {
	this.data = data;
	this.width = width;
	this.height = height;
	this.rangeFilter = rangeFilter;
	this.photometricStandardDeviation = photometricStandardDeviation;
	this.spatialDecay = spatialDecay;
} /* end BEEPSHorizontalVertical */

/*....................................................................
	Runnable methods
....................................................................*/
/*------------------------------------------------------------------*/
public void run (
) {
	BEEPSProgressive.setup(rangeFilter, photometricStandardDeviation,
		1.0 - spatialDecay);
	BEEPSGain.setup(1.0 - spatialDecay);
	BEEPSRegressive.setup(rangeFilter, photometricStandardDeviation,
		1.0 - spatialDecay);
	double[] g = new double[width * height];
	for (int k = 0, K = data.length; (k < K); k++) {
		g[k] = (double)data[k];
	}
	ExecutorService horizontalExecutor = Executors.newFixedThreadPool(
		Runtime.getRuntime().availableProcessors());
	double[] p = Arrays.copyOf(g, width * height);
	double[] r = Arrays.copyOf(g, width * height);
	for (int k2 = 0; (k2 < height); k2++) {
		Runnable progressive = new BEEPSProgressive(p, k2 * width, width);
		Runnable gain = new BEEPSGain(g, k2 * width, width);
		Runnable regressive = new BEEPSRegressive(r, k2 * width, width);
		horizontalExecutor.execute(progressive);
		horizontalExecutor.execute(gain);
		horizontalExecutor.execute(regressive);
	}
	try {
		horizontalExecutor.shutdown();
		horizontalExecutor.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
	}
	catch (InterruptedException ignored) {
	}
	for (int k = 0, K = data.length; (k < K); k++) {
		r[k] += p[k] - g[k];
	}
	int m = 0;
	for (int k2 = 0; (k2 < height); k2++) {
		int n = k2;
		for (int k1 = 0; (k1 < width); k1++) {
			g[n] = r[m++];
			n += height;
		}
	}
	ExecutorService verticalExecutor = Executors.newFixedThreadPool(
		Runtime.getRuntime().availableProcessors());
	p = Arrays.copyOf(g, height * width);
	r = Arrays.copyOf(g, height * width);
	for (int k1 = 0; (k1 < width); k1++) {
		Runnable progressive = new BEEPSProgressive(p, k1 * height, height);
		Runnable gain = new BEEPSGain(g, k1 * height, height);
		Runnable regressive = new BEEPSRegressive(r, k1 * height, height);
		verticalExecutor.execute(progressive);
		verticalExecutor.execute(gain);
		verticalExecutor.execute(regressive);
	}
	try {
		verticalExecutor.shutdown();
		verticalExecutor.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
	}
	catch (InterruptedException ignored) {
	}
	for (int k = 0, K = data.length; (k < K); k++) {
		r[k] += p[k] - g[k];
	}
	m = 0;
	for (int k1 = 0; (k1 < width); k1++) {
		int n = k1;
		for (int k2 = 0; (k2 < height); k2++) {
			data[n] = (float)r[m++];
			n += width;
		}
	}
} /* end run */

} /* end class BEEPSHorizontalVertical */

/*====================================================================
|	BEEPSProgressive
\===================================================================*/
/*------------------------------------------------------------------*/
class BEEPSProgressive
	implements
		Runnable

{ /* begin class BEEPSProgressive */

/*....................................................................
	private variables
....................................................................*/
private double[] data;
private int length;
private int startIndex;
private static double c;
private static double rho;
private static double spatialContraDecay;
private static int rangeFilter;

/*....................................................................
	BEEPSProgressive constructors
....................................................................*/
/*------------------------------------------------------------------*/
protected BEEPSProgressive (
	final double[] data,
	final int startIndex,
	final int length
) {
	this.data = data;
	this.startIndex = startIndex;
	this.length = length;
} /* end BEEPSProgressive */

/*....................................................................
	static methods
....................................................................*/
/*------------------------------------------------------------------*/
protected static void setup (
	final int sharedRangeFilter,
	final double photometricStandardDeviation,
	final double sharedSpatialContraDecay
) {
	rangeFilter = sharedRangeFilter;
	spatialContraDecay = sharedSpatialContraDecay;
	rho = 1.0 + spatialContraDecay;
	switch (rangeFilter) {
		case 0: { // gauss
			c = -0.5
				/ (photometricStandardDeviation * photometricStandardDeviation);
			break;
		}
		case 1: { // sech
			c = PI / (2.0 * photometricStandardDeviation);
			break;
		}
		case 2: { // tanh
			c = pow((0.75 * BEEPS_.ZETA_3) / (photometricStandardDeviation
				* photometricStandardDeviation), 1.0 / 3.0);
			c *= (photometricStandardDeviation < 0.0) ? (-1.0)
				: ((0.0 == photometricStandardDeviation) ? (0.0) : (1.0));
			break;
		}
		case 3: { // oneSided
			c = pow((0.75 * BEEPS_.ZETA_3) / (photometricStandardDeviation
				* photometricStandardDeviation), 1.0 / 3.0);
			c *= (photometricStandardDeviation < 0.0) ? (-1.0)
				: ((0.0 == photometricStandardDeviation) ? (0.0) : (1.0));
			break;
		}
	}
} /* end setup */

/*....................................................................
	Runnable methods
....................................................................*/
/*------------------------------------------------------------------*/
public void run (
) {
	double mu = 0.0;
	data[startIndex] /= rho;
	switch (rangeFilter) {
		case 0: { // gauss
			for (int k = startIndex + 1, K = startIndex + length;
				(k < K); k++) {
				mu = data[k] - rho * data[k - 1];
				mu = spatialContraDecay * exp(c * mu * mu);
				data[k] = data[k - 1] * mu + data[k] * (1.0 - mu) / rho;
			}
			break;
		}
		case 1: { // sech
			for (int k = startIndex + 1, K = startIndex + length;
				(k < K); k++) {
				mu = spatialContraDecay
					/ cosh(c * (data[k] - rho * data[k - 1]));
				data[k] = data[k - 1] * mu + data[k] * (1.0 - mu) / rho;
			}
			break;
		}
		case 2: { // tanh
			for (int k = startIndex + 1, K = startIndex + length;
				(k < K); k++) {
				mu = data[k] - rho * data[k - 1];
				mu = spatialContraDecay * tanh(c * mu);
				data[k] = data[k - 1] * mu + data[k] * (1.0 - mu) / rho;
			}
			break;
		}
		case 3: { // oneSided
			for (int k = startIndex + 1, K = startIndex + length;
				(k < K); k++) {
				mu = data[k] - rho * data[k - 1];
				mu = spatialContraDecay * (1.0 + tanh(c * mu)) * 0.5;
				data[k] = data[k - 1] * mu + data[k] * (1.0 - mu) / rho;
			}
			break;
		}
	}
} /* end run */

} /* end class BEEPSProgressive */

/*====================================================================
|	BEEPSRegressive
\===================================================================*/

/*------------------------------------------------------------------*/
class BEEPSRegressive
	implements
		Runnable

{ /* begin class BEEPSRegressive */

/*....................................................................
	private variables
....................................................................*/
private double[] data;
private int length;
private int startIndex;
private static double c;
private static double rho;
private static double spatialContraDecay;
private static int rangeFilter;

/*....................................................................
	BEEPSRegressive constructors
....................................................................*/
/*------------------------------------------------------------------*/
protected BEEPSRegressive (
	final double[] data,
	final int startIndex,
	final int length
) {
	this.data = data;
	this.startIndex = startIndex;
	this.length = length;
} /* end BEEPSRegressive */

/*....................................................................
	static methods
....................................................................*/
/*------------------------------------------------------------------*/
protected static void setup (
	final int sharedRangeFilter,
	final double photometricStandardDeviation,
	final double sharedSpatialContraDecay
) {
	rangeFilter = sharedRangeFilter;
	spatialContraDecay = sharedSpatialContraDecay;
	rho = 1.0 + spatialContraDecay;
	switch (rangeFilter) {
		case 0: { // gauss
			c = -0.5
				/ (photometricStandardDeviation * photometricStandardDeviation);
			break;
		}
		case 1: { // sech
			c = PI / (2.0 * photometricStandardDeviation);
			break;
		}
		case 2: { // tanh
			c = pow((0.75 * BEEPS_.ZETA_3) / (photometricStandardDeviation
				* photometricStandardDeviation), 1.0 / 3.0);
			c *= (photometricStandardDeviation < 0.0) ? (-1.0)
				: ((0.0 == photometricStandardDeviation) ? (0.0) : (1.0));
			break;
		}
		case 3: { // oneSided
			c = pow((0.75 * BEEPS_.ZETA_3) / (photometricStandardDeviation
				* photometricStandardDeviation), 1.0 / 3.0);
			c *= (photometricStandardDeviation < 0.0) ? (-1.0)
				: ((0.0 == photometricStandardDeviation) ? (0.0) : (1.0));
			break;
		}
	}
} /* end setup */

/*....................................................................
	Runnable methods
....................................................................*/
/*------------------------------------------------------------------*/
public void run (
) {
	double mu = 0.0;
	data[startIndex + length - 1] /= rho;
	switch (rangeFilter) {
		case 0: { // gauss
			for (int k = startIndex + length - 2; (startIndex <= k); k--) {
				mu = data[k] - rho * data[k + 1];
				mu = spatialContraDecay * exp(c * mu * mu);
				data[k] = data[k + 1] * mu + data[k] * (1.0 - mu) / rho;
			}
			break;
		}
		case 1: { // sech
			for (int k = startIndex + length - 2; (startIndex <= k); k--) {
				mu = spatialContraDecay
					/ cosh(c * (data[k] - rho * data[k + 1]));
				data[k] = data[k + 1] * mu + data[k] * (1.0 - mu) / rho;
			}
			break;
		}
		case 2: { // tanh
			for (int k = startIndex + length - 2; (startIndex <= k); k--) {
				mu = data[k] - rho * data[k + 1];
				mu = spatialContraDecay * tanh(c * mu);
				data[k] = data[k + 1] * mu + data[k] * (1.0 - mu) / rho;
			}
			break;
		}
		case 3: { // oneSided
			for (int k = startIndex + length - 2; (startIndex <= k); k--) {
				mu = data[k] - rho * data[k + 1];
				mu = spatialContraDecay * (1.0 + tanh(c * mu)) * 0.5;
				data[k] = data[k + 1] * mu + data[k] * (1.0 - mu) / rho;
			}
			break;
		}
	}
} /* end run */

} /* end class BEEPSRegressive */

/*====================================================================
|	BEEPSVerticalHorizontal
\===================================================================*/
/*------------------------------------------------------------------*/
class BEEPSVerticalHorizontal
	implements
		Runnable

{ /* begin class BEEPSVerticalHorizontal */

/*....................................................................
	private variables
....................................................................*/
private double photometricStandardDeviation;
private double spatialDecay;
private float[] data;
private int height;
private int rangeFilter;
private int width;

/*....................................................................
	BEEPSVerticalHorizontal constructors
....................................................................*/
/*------------------------------------------------------------------*/
protected BEEPSVerticalHorizontal (
	final float[] data,
	final int width,
	final int height,
	final int rangeFilter,
	final double photometricStandardDeviation,
	final double spatialDecay
) {
	this.data = data;
	this.width = width;
	this.height = height;
	this.rangeFilter = rangeFilter;
	this.photometricStandardDeviation = photometricStandardDeviation;
	this.spatialDecay = spatialDecay;
} /* end BEEPSVerticalHorizontal */

/*....................................................................
	Runnable methods
....................................................................*/
/*------------------------------------------------------------------*/
public void run (
) {
	BEEPSProgressive.setup(rangeFilter, photometricStandardDeviation,
		1.0 - spatialDecay);
	BEEPSGain.setup(1.0 - spatialDecay);
	BEEPSRegressive.setup(rangeFilter, photometricStandardDeviation,
		1.0 - spatialDecay);
	double[] g = new double[height * width];
	int m = 0;
	for (int k2 = 0; (k2 < height); k2++) {
		int n = k2;
		for (int k1 = 0; (k1 < width); k1++) {
			g[n] = (double)data[m++];
			n += height;
		}
	}
	ExecutorService verticalExecutor = Executors.newFixedThreadPool(
		Runtime.getRuntime().availableProcessors());
	double[] p = Arrays.copyOf(g, height * width);
	double[] r = Arrays.copyOf(g, height * width);
	for (int k1 = 0; (k1 < width); k1++) {
		Runnable progressive = new BEEPSProgressive(p, k1 * height, height);
		Runnable gain = new BEEPSGain(g, k1 * height, height);
		Runnable regressive = new BEEPSRegressive(r, k1 * height, height);
		verticalExecutor.execute(progressive);
		verticalExecutor.execute(gain);
		verticalExecutor.execute(regressive);
	}
	try {
		verticalExecutor.shutdown();
		verticalExecutor.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
	}
	catch (InterruptedException ignored) {
	}
	for (int k = 0, K = data.length; (k < K); k++) {
		r[k] += p[k] - g[k];
	}
	m = 0;
	for (int k1 = 0; (k1 < width); k1++) {
		int n = k1;
		for (int k2 = 0; (k2 < height); k2++) {
			g[n] = r[m++];
			n += width;
		}
	}
	ExecutorService horizontalExecutor = Executors.newFixedThreadPool(
		Runtime.getRuntime().availableProcessors());
	p = Arrays.copyOf(g, width * height);
	r = Arrays.copyOf(g, width * height);
	for (int k2 = 0; (k2 < height); k2++) {
		Runnable progressive = new BEEPSProgressive(p, k2 * width, width);
		Runnable gain = new BEEPSGain(g, k2 * width, width);
		Runnable regressive = new BEEPSRegressive(r, k2 * width, width);
		horizontalExecutor.execute(progressive);
		horizontalExecutor.execute(gain);
		horizontalExecutor.execute(regressive);
	}
	try {
		horizontalExecutor.shutdown();
		horizontalExecutor.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
	}
	catch (InterruptedException ignored) {
	}
	for (int k = 0, K = data.length; (k < K); k++) {
		data[k] = (float)(p[k] - g[k] + r[k]);
	}
} /* end run */

} /* end class BEEPSVerticalHorizontal */
