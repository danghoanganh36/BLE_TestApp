package com.example.ble_brainlife;
import com.github.psambit9791.jdsp.filter.Butterworth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public class EGGSignalProcessor {
    private static final double EPS = 2.220446049250313e-16; // Machine epsilon
    /**
     * Complex number class for filter design calculations
     */
    public static class Complex {
        public double real, imag;
        public Complex(double real, double imag) {
            this.real = real;
            this.imag = imag;
        }
        public Complex(double real) {
            this(real, 0);
        }
        public Complex add(Complex other) {
            return new Complex(this.real + other.real, this.imag + other.imag);
        }
        public Complex subtract(Complex other) {
            return new Complex(this.real - other.real, this.imag - other.imag);
        }
        public Complex multiply(Complex other) {
            return new Complex(
                    this.real * other.real - this.imag * other.imag,
                    this.real * other.imag + this.imag * other.real
            );
        }
        public Complex divide(Complex other) {
            double denom = other.real * other.real + other.imag * other.imag;
            return new Complex(
                    (this.real * other.real + this.imag * other.imag) / denom,
                    (this.imag * other.real - this.real * other.imag) / denom
            );
        }
        public double magnitude() {
            return Math.sqrt(real * real + imag * imag);
        }
        public double phase() {
            return Math.atan2(imag, real);
        }
        public Complex conjugate() {
            return new Complex(real, -imag);
        }
        public static Complex exp(Complex z) {
            double expReal = Math.exp(z.real);
            return new Complex(expReal * Math.cos(z.imag), expReal * Math.sin(z.imag));
        }
        public static Complex polar(double magnitude, double phase) {
            return new Complex(magnitude * Math.cos(phase), magnitude * Math.sin(phase));
        }
    }
    /**
     * Filter coefficients container
     */
    public static class FilterCoefficients {
        public double[] b; // Numerator coefficients
        public double[] a; // Denominator coefficients
        public FilterCoefficients(double[] b, double[] a) {
            this.b = b.clone();
            this.a = a.clone();
        }
    }
    /**
     * DC blocking filter implementation
     *
     * @param data  Input signal data
     * @param alpha Filter coefficient (default 0.99)
     * @return Filtered signal with DC component removed
     */
    public static List<Double> dcBlockingFilter(List<Double> data, double alpha) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Input data cannot be null or empty");
        }
        List<Double> y = new ArrayList<>();
        y.add(data.get(0));                 // y[0] = data[0]
        for (int i = 1; i < data.size(); i++) {
            double value = data.get(i) - data.get(i - 1) + alpha * y.get(i - 1); // y[i] = data[i] - data[i-1] + alpha * y[i-1]
            y.add(value);
        }
        return y;
    }
    public static List<Double> dcBlockingFilter(List<Double> data) {
        return dcBlockingFilter(data, 0.99);
    }
    /**
     * IIR Notch filter implementation matching SciPy's iirnotch
     */
    public static FilterCoefficients iirNotchScipy(double w0, double Q) {
        // w0 is normalized frequency (0 to 1, where 1 is Nyquist)
        if (w0 <= 0 || w0 >= 1) {
            throw new IllegalArgumentException("w0 must be between 0 and 1");
        }
        // Convert to angular frequency
        double omega = Math.PI * w0;
        double alpha = Math.sin(omega) / (2.0 * Q);
        double cosOmega = Math.cos(omega);
        // Direct form II coefficients
        double[] b = {1.0, -2.0 * cosOmega, 1.0};
        double[] a = {1.0 + alpha, -2.0 * cosOmega, 1.0 - alpha};
        // Normalize by a[0]
        double a0 = a[0];
        for (int i = 0; i < b.length; i++) {
            b[i] /= a0;
        }
        for (int i = 0; i < a.length; i++) {
            a[i] /= a0;
        }
        return new FilterCoefficients(b, a);
    }
    /**
     * Linear filter implementation - exact match to SciPy's lfilter
     * Uses Direct Form II transposed structure
     */
    public static List<Double> lfilter(double[] b, double[] a, List<Double> x) {
        if (x == null || x.isEmpty()) {
            return new ArrayList<>();
        }
        int n = x.size();
        int nb = b.length;
        int na = a.length;
        int nfilt = Math.max(nb, na);
        List<Double> y = new ArrayList<>(Collections.nCopies(n, 0.0));
        double[] zi = new double[nfilt - 1]; // Internal state
        // Normalize coefficients by a[0]
        double[] bNorm = new double[nb];
        double[] aNorm = new double[na];
        double a0 = (na > 0) ? a[0] : 1.0;
        for (int i = 0; i < nb; i++) {
            bNorm[i] = b[i] / a0;
        }
        for (int i = 0; i < na; i++) {
            aNorm[i] = a[i] / a0;
        }
        // Direct Form II Transposed implementation
        for (int m = 0; m < n; m++) {
            double input = x.get(m);
            double output = bNorm[0] * input + ((zi.length > 0) ? zi[0] : 0.0);
            // Update delay line
            for (int i = 0; i < zi.length - 1; i++) {
                zi[i] = bNorm[Math.min(i + 1, nb - 1)] * input
                        - aNorm[Math.min(i + 1, na - 1)] * output
                        + zi[i + 1];
            }
            if (zi.length > 0) {
                int lastIdx = zi.length - 1;
                zi[lastIdx] = bNorm[Math.min(lastIdx + 1, nb - 1)] * input
                        - aNorm[Math.min(lastIdx + 1, na - 1)] * output;
            }
            y.set(m, output);
        }
        return y;
    }
    /**
     * Apply bandpass filter to data using jDSP library
     *
     * @param data Input signal data
     * @param lowcut Low cutoff frequency in Hz
     * @param highcut High cutoff frequency in Hz
     * @param fs Sampling frequency in Hz
     * @param order Filter order (default 4)
     * @return Filtered signal
     */
    public static List<Double> bandpassFilter(List<Double> data, double lowcut, double highcut, double fs, int order) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Input data cannot be null or empty");
        }
        // Convert List<Double> to double array for jDSP
        double[] inputArray = data.stream().mapToDouble(Double::doubleValue).toArray();
        // Create Butterworth bandpass filter using jDSP
        Butterworth bpFilter = new Butterworth((int) fs);
        // Apply bandpass filter with specified parameters
        double[] filteredArray = bpFilter.bandPassFilter(inputArray, order, lowcut, highcut);
        // Convert back to List<Double>
        List<Double> result = new ArrayList<>();
        for (double value : filteredArray) {
            result.add(value);
        }
        return result;
    }
    public static List<Double> bandpassFilter(List<Double> data, double lowcut, double highcut, double fs) {
        return bandpassFilter(data, lowcut, highcut, fs, 4);
    }
    /**
     * IIR Notch filter implementation matching SciPy's iirnotch
     *
     * @param data      Input signal
     * @param notchFreq Frequency to notch out
     * @param fs        Sampling frequency
     * @param Q         Quality factor
     * @return Filtered signal
     */
    public static List<Double> notchFilter(List<Double> data, double notchFreq, double fs, double Q) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Input data can not be null or empty");
        }
        if (notchFreq <= 0 || notchFreq >= fs/2 || Q <= 0) {
            throw new IllegalArgumentException("Invalid notch parameters: 0 < notchFreq < fs/2, Q > 0");
        }
        double w0 = 2.0 * notchFreq / fs;
        FilterCoefficients coeffs = iirNotchScipy(w0, Q);
        return lfilter(coeffs.b, coeffs.a, data);
    }
    public static List<Double> notchFilter(List<Double> data, double notchFreq, double fs) {
        return notchFilter(data, notchFreq, fs, 30.0);
    }
    /**
     * Complete EEG preprocessing pipeline
     */
    public static List<Double> preprocessEEG(List<Double> data, double fs) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Input data cannot be null or empty");
        }
        List<Double> dcRemoved = dcBlockingFilter(data);          // DC Blocking filter
        List<Double> notch60 = notchFilter(dcRemoved, 60, fs, 12);     // Notch filter 60Hz
        List<Double> notch50 = notchFilter(notch60, 50, fs, 5);     // Notch filter 50Hz
        List<Double> notch32 = notchFilter(notch50, 32, fs, 10);     // Notch filter 32Hz
        // Bandpass filter using jDSP
        return bandpassFilter(notch32, 0.5, 35, fs);
    }
    public static List<Double> preprocessEEG(List<Double> data) {
        return preprocessEEG(data, 244.0);
    }
    public static boolean containsNaN(List<Double> array) {
        if (array == null) return false;
        for (double value : array) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return true;
            }
        }
        return false;
    }
}