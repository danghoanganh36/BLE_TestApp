package com.example.ble_brainlife;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EEGSignalProcessorTest {

    private static final double DELTA = 1e-6; // Tolerance for floating point comparisons

    @Test
    public void testComplex() {
        EGGSignalProcessor.Complex c1 = new EGGSignalProcessor.Complex(3, 4);
        EGGSignalProcessor.Complex c2 = new EGGSignalProcessor.Complex(1, 2);

        // Test addition
        EGGSignalProcessor.Complex sum = c1.add(c2);
        assertEquals(4, sum.real, DELTA);
        assertEquals(6, sum.imag, DELTA);

        // Test subtraction
        EGGSignalProcessor.Complex diff = c1.subtract(c2);
        assertEquals(2, diff.real, DELTA);
        assertEquals(2, diff.imag, DELTA);

        // Test multiplication
        EGGSignalProcessor.Complex prod = c1.multiply(c2);
        assertEquals(-5, prod.real, DELTA);
        assertEquals(10, prod.imag, DELTA);

        // Test division
        EGGSignalProcessor.Complex quot = c1.divide(c2);
        assertEquals(2.2, quot.real, DELTA);
        assertEquals(0, quot.imag, DELTA);

        // Test magnitude
        assertEquals(5, c1.magnitude(), DELTA);

        // Test conjugate
        EGGSignalProcessor.Complex conj = c1.conjugate();
        assertEquals(3, conj.real, DELTA);
        assertEquals(-4, conj.imag, DELTA);
    }

    @Test
    public void testDcBlockingFilter() {
        List<Double> input = Arrays.asList(5.0, 5.0, 5.0, 5.0, 5.0);
        List<Double> output = EGGSignalProcessor.dcBlockingFilter(input);

        // DC signal should approach zero after filtering
        assertEquals(5.0, output.get(0), DELTA);
        assertTrue(Math.abs(output.get(output.size() - 1)) < 0.1);
    }

    @Test
    public void testDcBlockingFilterWithAlpha() {
        List<Double> input = Arrays.asList(5.0, 5.0, 5.0, 5.0, 5.0);
        List<Double> output = EGGSignalProcessor.dcBlockingFilter(input, 0.8);

        assertEquals(5.0, output.get(0), DELTA);
        assertTrue(Math.abs(output.get(output.size() - 1)) < 0.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDcBlockingFilterWithEmptyInput() {
        EGGSignalProcessor.dcBlockingFilter(new ArrayList<>());
    }

    @Test
    public void testIirNotchScipy() {
        EGGSignalProcessor.FilterCoefficients coeffs = EGGSignalProcessor.iirNotchScipy(0.5, 10);

        // Check dimensions
        assertEquals(3, coeffs.b.length);
        assertEquals(3, coeffs.a.length);

        // Coefficients sum checks (for properly normalized coefficients)
        assertEquals(0.0, coeffs.b[0] + coeffs.b[1] + coeffs.b[2], 0.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIirNotchScipyInvalidFrequency() {
        EGGSignalProcessor.iirNotchScipy(1.5, 10); // w0 > 1
    }

    @Test
    public void testLfilter() {
        double[] b = {1.0};
        double[] a = {1.0};
        List<Double> input = Arrays.asList(1.0, 2.0, 3.0, 4.0);

        List<Double> output = EGGSignalProcessor.lfilter(b, a, input);

        assertEquals(input.size(), output.size());
        for (int i = 0; i < input.size(); i++) {
            assertEquals(input.get(i), output.get(i), DELTA);
        }
    }

    @Test
    public void testBandpassFilter() {
        List<Double> input = new ArrayList<>();
        // Create a signal with multiple frequencies
        for (int i = 0; i < 1000; i++) {
            double t = i / 244.0;  // Time at 244 Hz sampling rate
            // Add 5 Hz, 20 Hz and 80 Hz components
            input.add(Math.sin(2 * Math.PI * 5 * t) +
                    Math.sin(2 * Math.PI * 20 * t) +
                    Math.sin(2 * Math.PI * 80 * t));
        }

        List<Double> output = EGGSignalProcessor.bandpassFilter(input, 10, 30, 244.0);

        assertNotNull(output);
        assertEquals(input.size(), output.size());
        // Sophisticated test would require spectral analysis
        assertFalse(EGGSignalProcessor.containsNaN(output));
    }

    @Test
    public void testNotchFilter() {
        List<Double> input = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            double t = i / 244.0;
            input.add(Math.sin(2 * Math.PI * 60 * t));  // 60 Hz signal
        }

        List<Double> output = EGGSignalProcessor.notchFilter(input, 60, 244.0, 30);

        assertNotNull(output);
        // The amplitude of a 60 Hz signal should be significantly reduced
        double inputAmplitude = calculateRMS(input);
        double outputAmplitude = calculateRMS(output);

        assertTrue(outputAmplitude < inputAmplitude * 0.5);
    }

    @Test
    public void testPreprocessEEG() {
        List<Double> input = new ArrayList<>();
        // Create a complex signal
        for (int i = 0; i < 1000; i++) {
            double t = i / 244.0;
            input.add(10.0 + Math.sin(2 * Math.PI * 5 * t) +
                    Math.sin(2 * Math.PI * 50 * t) +
                    Math.sin(2 * Math.PI * 60 * t));
        }

        List<Double> output = EGGSignalProcessor.preprocessEEG(input);

        assertNotNull(output);
        assertEquals(input.size(), output.size());
        assertFalse(EGGSignalProcessor.containsNaN(output));

        // DC component should be removed
        double mean = calculateMean(output);
        assertTrue(Math.abs(mean) < 0.1);
    }

    @Test
    public void testContainsNaN() {
        List<Double> validList = Arrays.asList(1.0, 2.0, 3.0);
        assertFalse(EGGSignalProcessor.containsNaN(validList));

        List<Double> nanList = Arrays.asList(1.0, Double.NaN, 3.0);
        assertTrue(EGGSignalProcessor.containsNaN(nanList));

        List<Double> infList = Arrays.asList(1.0, Double.POSITIVE_INFINITY, 3.0);
        assertTrue(EGGSignalProcessor.containsNaN(infList));
    }

    // Helper methods
    private double calculateMean(List<Double> data) {
        double sum = 0;
        for (double value : data) {
            sum += value;
        }
        return sum / data.size();
    }

    private double calculateRMS(List<Double> data) {
        double sumOfSquares = 0;
        for (double value : data) {
            sumOfSquares += value * value;
        }
        return Math.sqrt(sumOfSquares / data.size());
    }
}