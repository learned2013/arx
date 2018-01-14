/*
 * ARX: Powerful Data Anonymization
 * Copyright 2012 - 2017 Fabian Prasser, Florian Kohlmayer and contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.deidentifier.arx.gui.view.impl.utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.deidentifier.arx.ARXClassificationConfiguration;
import org.deidentifier.arx.ARXFeatureScaling;
import org.deidentifier.arx.aggregates.StatisticsBuilderInterruptible;
import org.deidentifier.arx.aggregates.StatisticsClassification;
import org.deidentifier.arx.aggregates.StatisticsClassification.ROCCurve;
import org.deidentifier.arx.gui.Controller;
import org.deidentifier.arx.gui.model.ModelEvent;
import org.deidentifier.arx.gui.model.ModelEvent.ModelPart;
import org.deidentifier.arx.gui.resources.Resources;
import org.deidentifier.arx.gui.view.SWTUtil;
import org.deidentifier.arx.gui.view.impl.common.ClipboardHandlerTable;
import org.deidentifier.arx.gui.view.impl.common.ComponentStatusLabelProgressProvider;
import org.deidentifier.arx.gui.view.impl.common.ComponentTitledFolder;
import org.deidentifier.arx.gui.view.impl.common.ComponentTitledFolderButtonBar;
import org.deidentifier.arx.gui.view.impl.common.async.Analysis;
import org.deidentifier.arx.gui.view.impl.common.async.AnalysisContext;
import org.deidentifier.arx.gui.view.impl.common.async.AnalysisManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.swtchart.Chart;
import org.swtchart.IAxis;
import org.swtchart.IAxisSet;
import org.swtchart.ILineSeries;
import org.swtchart.ILineSeries.PlotSymbolType;
import org.swtchart.ISeries;
import org.swtchart.ISeries.SeriesType;
import org.swtchart.ISeriesSet;
import org.swtchart.ITitle;
import org.swtchart.Range;

import de.linearbits.swt.table.DynamicTable;
import de.linearbits.swt.table.DynamicTableColumn;

/**
 * This view displays a statistics about the performance of logistic regression classifiers
 *
 * @author Fabian Prasser
 * @author Johanna Eicher
 */
public abstract class ViewStatisticsClassification extends ViewStatistics<AnalysisContextClassification> {

    /** Minimal width of a category label. */
    private static final int                   MIN_CATEGORY_WIDTH = 10;

    /** Internal stuff. */
    private AnalysisManager                    manager;

    /** View */
    private ViewStatisticsClassification       other;
    /** View */
    private ComponentTitledFolder              folder;
    /** View */
    private DynamicTable                       performancePerTargetTable;
    /** View */
    private DynamicTable                       performancePerClassTable;
    /** View */
    private Composite                          performanceRoot;
    /** View */
    private SashForm                           performanceSash;
    /** View */
    private DynamicTable                       rocTable;
    /** View */
    private Composite                          rocRoot;
    /** View */
    private SashForm                           rocSash;
    /** View */
    private Chart                              rocChart;
    /** Widget */
    private Combo                              rocCombo;

    /** Model */
    private boolean                            isOutput;
    /** Model */
    private Map<String, Map<String, ROCCurve>> rocCurves;
    /** Model */
    private Map<String, Map<String, ROCCurve>> originalRocCurves;
    /** Model */
    private Map<String, Map<String, ROCCurve>> zerorRocCurves;

    /**
     * Creates a new instance.
     *
     * @param parent
     * @param controller
     * @param part
     */
    public ViewStatisticsClassification(final Composite parent,
                                        final Controller controller,
                                        final ModelPart part) {

        super(parent, controller, part, null, false);
        this.manager = new AnalysisManager(parent.getDisplay());
        this.isOutput = part != ModelPart.INPUT;
        this.rocCurves = new HashMap<>();
        this.originalRocCurves = new HashMap<>();
        this.zerorRocCurves = new HashMap<>();
        
        controller.addListener(ModelPart.CLASSIFICATION_CONFIGURATION, this);
        controller.addListener(ModelPart.DATA_TYPE, this);
        controller.addListener(ModelPart.SELECTED_ATTRIBUTE, this);
        controller.addListener(ModelPart.SELECTED_CLASS_VALUE, this);
    }
    
    @Override
    public LayoutUtility.ViewUtilityType getType() {
        return LayoutUtility.ViewUtilityType.CLASSIFICATION_PRECISION_RECALL;
    }
    
    @Override
    public void update(ModelEvent event) {
        super.update(event);
        if (event.part == ModelPart.CLASSIFICATION_CONFIGURATION ||
            event.part == ModelPart.DATA_TYPE) {
            if (getModel() != null && (getModel().getSelectedFeatures().isEmpty() || getModel().getSelectedClasses().isEmpty())) {
                doReset();
                return;
            } else {
                triggerUpdate();
            }
        }
        
        if (event.part == ModelPart.SELECTED_ATTRIBUTE) {
            
            if (originalRocCurves.containsKey(getModel().getSelectedAttribute())) {
                List<String> classes = new ArrayList<>(originalRocCurves.get(getModel().getSelectedAttribute()).keySet());
                Collections.sort(classes);
                getModel().setSelectedClassValue(classes.get(0));
                updateOverviewSelection(super.getModel().getSelectedAttribute());
                updateROCSelection(super.getModel().getSelectedClassValue());
                getController().update(new ModelEvent(ViewStatisticsClassification.this,
                                                      ModelPart.SELECTED_CLASS_VALUE,
                                                      getModel().getSelectedClassValue()));
            }
        }
        
        if (event.part == ModelPart.SELECTED_CLASS_VALUE) {
            updateROCSelection(super.getModel().getSelectedClassValue());
        }
    }

    /**
     * Builds overall performance view
     * @param parent
     * @return
     */
    private Control createOverviewControl(Composite parent) {

        // Root
        this.performanceRoot = parent;
        this.performanceRoot.setLayout(new FillLayout());
        
        // Sash
        this.performanceSash = new SashForm(this.performanceRoot, SWT.VERTICAL);
        
        // Table: performance per target
        this.performancePerTargetTable = SWTUtil.createTableDynamic(this.performanceSash, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.FULL_SELECTION);
        this.performancePerTargetTable.setHeaderVisible(true);
        this.performancePerTargetTable.setLinesVisible(true);
        this.performancePerTargetTable.setMenu(new ClipboardHandlerTable(performancePerTargetTable).getMenu());

        // Columns
        String[] columns = getColumnHeadersForPerformanceForOverallPerformanceTable();
        String width = String.valueOf(Math.round(100d / ((double) columns.length + 2) * 100d) / 100d) + "%"; //$NON-NLS-1$
        // Column for target
        DynamicTableColumn c = new DynamicTableColumn(performancePerTargetTable, SWT.LEFT);
        c.setWidth(width, "100px"); //$NON-NLS-1$
        c.setText(Resources.getMessage("ViewStatisticsClassificationInput.0")); //$NON-NLS-1$
        for (String column : columns) {
            c = new DynamicTableColumn(performancePerTargetTable, SWT.LEFT);
            SWTUtil.createColumnWithBarCharts(performancePerTargetTable, c);
            c.setWidth(width, "100px"); //$NON-NLS-1$ 
            c.setText(column);
        }
        for (final TableColumn col : performancePerTargetTable.getColumns()) {
            col.pack();
        }
        SWTUtil.createGenericTooltip(performancePerTargetTable);
        
        // Update table
        performancePerTargetTable.addListener(SWT.MouseDown, new Listener() {
            public void handleEvent(Event event) {
                Rectangle clientArea = performancePerTargetTable.getClientArea();
                Point pt = new Point(event.x, event.y);
                int index = performancePerTargetTable.getTopIndex();
                while (index < performancePerTargetTable.getItemCount()) {
                    boolean visible = false;
                    TableItem item = performancePerTargetTable.getItem(index);
                    for (int i = 0; i < performancePerTargetTable.getColumnCount(); i++) {
                        Rectangle rect = item.getBounds(i);
                        if (rect.contains(pt)) {
                            getModel().setSelectedAttribute(item.getText(0));
                            List<String> classes = new ArrayList<>(originalRocCurves.get(item.getText(0)).keySet());
                            Collections.sort(classes);
                            getModel().setSelectedClassValue(classes.get(0));
                            updateOverview(getModel().getSelectedAttribute());
                            updateROCSelection(classes.get(0));
                            getController().update(new ModelEvent(ViewStatisticsClassification.this,
                                                                  ModelPart.SELECTED_ATTRIBUTE,
                                                                  getModel().getSelectedAttribute()));
                            getController().update(new ModelEvent(ViewStatisticsClassification.this,
                                                                  ModelPart.SELECTED_CLASS_VALUE,
                                                                  getModel().getSelectedClassValue()));
                            return;
                        }
                        if (!visible && rect.intersects(clientArea)) {
                            visible = true;
                        }
                    }
                    if (!visible) return;
                    index++;
                }
            }
        });
        
        // Table: performance for each class of a target
        this.performancePerClassTable = SWTUtil.createTableDynamic(this.performanceSash, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.FULL_SELECTION);
        this.performancePerClassTable.setHeaderVisible(true);
        this.performancePerClassTable.setLinesVisible(true);
        this.performancePerClassTable.setMenu(new ClipboardHandlerTable(performancePerClassTable).getMenu());
        
        width = String.valueOf(Math.round(100d / ((double) 4) * 100d) / 100d) + "%"; //$NON-NLS-1$
        // Column for class
        c = new DynamicTableColumn(performancePerClassTable, SWT.LEFT);
        c.setWidth(width, "100px"); //$NON-NLS-1$
        c.setText(Resources.getMessage("ViewStatisticsClassificationInput.22")); //$NON-NLS-1$
        // Column for sensitivity
        c = new DynamicTableColumn(performancePerClassTable, SWT.LEFT);
        SWTUtil.createColumnWithBarCharts(performancePerClassTable, c);
        c.setWidth(width, "100px"); //$NON-NLS-1$
        c.setText(Resources.getMessage("ViewStatisticsClassificationInput.11")); //$NON-NLS-1$
        // Column for specificity
        c = new DynamicTableColumn(performancePerClassTable, SWT.LEFT);
        SWTUtil.createColumnWithBarCharts(performancePerClassTable, c);
        c.setWidth(width, "100px"); //$NON-NLS-1$
        c.setText(Resources.getMessage("ViewStatisticsClassificationInput.10")); //$NON-NLS-1$
        // Column for brier score
        c = new DynamicTableColumn(performancePerClassTable, SWT.LEFT);
        SWTUtil.createColumnWithBarCharts(performancePerClassTable, c);
        c.setWidth(width, "100px"); //$NON-NLS-1$
        c.setText(Resources.getMessage("ViewStatisticsClassificationInput.8")); //$NON-NLS-1$
        for (final TableColumn col : performancePerClassTable.getColumns()) {
            col.pack();
        }
        SWTUtil.createGenericTooltip(performancePerClassTable);

        this.performanceSash.setWeights(new int[] {2, 2});
        return this.performanceRoot;
    }
    
    /**
     * Creates control for ROC curves
     * @param parent
     * @return
     */
    private Control createROCControl(Composite parent) {
        
        // Root
        this.rocRoot = parent;
        this.rocRoot.setLayout(new FillLayout());
        
        // Sash
        this.rocSash = new SashForm(this.rocRoot, SWT.VERTICAL);
        
        final Composite composite = new Composite(this.rocSash, SWT.NONE);
        composite.setLayout(SWTUtil.createGridLayout(1));
        
        // Combo for selecting a target variable
        final Composite composite2 = new Composite(composite, SWT.NONE);
        composite2.setLayoutData(SWTUtil.createFillHorizontallyGridData());
        composite2.setLayout(SWTUtil.createGridLayout(2, false));
        final Label lblTargetVariable = new Label(composite2, SWT.PUSH);
        lblTargetVariable.setText(Resources.getMessage("ViewStatisticsClassificationInput.21")); //$NON-NLS-1$        
        this.rocCombo = new Combo(composite2, SWT.READ_ONLY);
        this.rocCombo.setLayoutData(SWTUtil.createFillHorizontallyGridData());
        this.rocCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent arg0) {
                if(rocCombo.getSelectionIndex() >=0){
                    // Update target variable
                    String selectedTarget = rocCombo.getItem(rocCombo.getSelectionIndex());
                    getModel().setSelectedAttribute(selectedTarget);
                    // Update class
                    List<String> classes = new ArrayList<>(originalRocCurves.get(selectedTarget).keySet());
                    Collections.sort(classes);
                    getModel().setSelectedClassValue(classes.get(0));
                    // Update performance overview view
                    updateOverviewSelection(selectedTarget);
                    // Update roc view
                    updateROCSelection(classes.get(0));
                    getController().update(new ModelEvent(ViewStatisticsClassification.this,
                                                          ModelPart.SELECTED_ATTRIBUTE,
                                                          selectedTarget));
                }
            }
        });

        // Table
        this.rocTable = SWTUtil.createTableDynamic(composite, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.FULL_SELECTION);
        this.rocTable.setHeaderVisible(true);
        this.rocTable.setLinesVisible(true);
        this.rocTable.setMenu(new ClipboardHandlerTable(rocTable).getMenu());
        this.rocTable.setLayoutData(SWTUtil.createFillGridData(2));
        
        // Columns
        String[] columns = getColumnHeadersForAUCTable();
        String  width = String.valueOf(Math.round(100d / ((double) (getTarget() == ModelPart.OUTPUT ? 4 : 3)) * 100d) / 100d) + "%"; //$NON-NLS-1$
        
        // Column for class
        DynamicTableColumn c = new DynamicTableColumn(rocTable, SWT.LEFT);
        c.setWidth(width, "100px"); //$NON-NLS-1$
        c.setText(Resources.getMessage("ViewStatisticsClassificationInput.22")); //$NON-NLS-1$
        for (String column : columns) {
            c = new DynamicTableColumn(rocTable, SWT.LEFT);
            SWTUtil.createColumnWithBarCharts(rocTable, c);
            c.setWidth(width, "100px"); //$NON-NLS-1$ 
            c.setText(column);
        }
        for (final TableColumn col : performancePerTargetTable.getColumns()) {
            col.pack();
        }
        SWTUtil.createGenericTooltip(performancePerTargetTable);
        
        // Chart and sash
        resetChart();
        this.rocSash.setWeights(new int[] {2, 2});
        
        // Tool tip
        final StringBuilder builder = new StringBuilder();
        this.rocSash.addListener(SWT.MouseMove, new Listener() {
            @Override
            public void handleEvent(Event event) {
                if (rocChart != null) {
                    IAxisSet axisSet = rocChart.getAxisSet();
                    if (axisSet != null) {
                        IAxis xAxis = axisSet.getXAxis(0);
                        IAxis yAxis = axisSet.getYAxis(0);
                        if (xAxis != null && yAxis != null) {
                            Point cursor = rocChart.getPlotArea().toControl(Display.getCurrent().getCursorLocation());
                            if (cursor.x >= 0 && cursor.x < rocChart.getPlotArea().getSize().x && cursor.y >= 0 && cursor.y < rocChart.getPlotArea().getSize().y) {
                                ISeries[] data = rocChart.getSeriesSet().getSeries();
                                if (data != null && data.length > 0) {
                                    // Use baseline for tool tip
                                    double[] x = data[0].getXSeries();
                                    double[] y = data[0].getYSeries();
                                    int index = getIndex(x, xAxis.getDataCoordinate(cursor.x));
                                    if (index >= 0) {
                                        builder.setLength(0);
                                        builder.append("("); //$NON-NLS-1$
                                        builder.append(Resources.getMessage("ViewStatisticsClassificationInput.20")).append(": "); //$NON-NLS-1$ //$NON-NLS-3$
                                        builder.append(SWTUtil.getPrettyString(x[index])).append(", "); //$NON-NLS-1$
                                        builder.append(Resources.getMessage("ViewStatisticsClassificationInput.19")).append(": "); //$NON-NLS-1$ //$NON-NLS-3$
                                        builder.append(SWTUtil.getPrettyString(y[index]));
                                        builder.append(")"); //$NON-NLS-1$
                                        rocSash.setToolTipText(builder.toString());
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    rocSash.setToolTipText(null);
                }
            }
        });

        // Update curve
        rocTable.addListener(SWT.MouseDown, new Listener() {
            public void handleEvent(Event event) {
                Rectangle clientArea = rocTable.getClientArea();
                Point pt = new Point(event.x, event.y);
                int index = rocTable.getTopIndex();
                while (index < rocTable.getItemCount()) {
                    boolean visible = false;
                    TableItem item = rocTable.getItem(index);
                    for (int i = 0; i < rocTable.getColumnCount(); i++) {
                        Rectangle rect = item.getBounds(i);
                        if (rect.contains(pt)) {
                            if (item.getData() != null) {
                                rocSetChartSeries((ROCCurve[]) item.getData());
                            }
                            getModel().setSelectedClassValue(item.getText(0));
                            getController().update(new ModelEvent(ViewStatisticsClassification.this,
                                                                  ModelPart.SELECTED_CLASS_VALUE,
                                                                  item.getText(0)));
                            return;
                        }
                        if (!visible && rect.intersects(clientArea)) {
                            visible = true;
                        }
                    }
                    if (!visible) return;
                    index++;
                }
            }
        });

        return this.rocRoot;
    }
    
    /**
     * Returns the value of the given point
     * 
     * @param data
     * @param value
     * @return
     */
    private int getIndex(double[] data, double value){
        int index = Arrays.binarySearch(data, value);
        if (index < 0) {
            index = -index + 1;
        }
        if (index > data.length - 1) {
            index = data.length - 1;
        }
        return index;
    }

    /**
     * Returns the index of the given value, 0 if it is not found
     * @param values
     * @param value
     * @return
     */
    private int getIndexOf(String[] values, String value) {
        int index = 0;
        for (String element : values) {
            if (element.equals(value)) {
                return index; 
            }
            index++;
        }
        return 0;
    }

    /**
     * Resets the chart
     */
    private void resetChart() {

        if (rocChart != null) {
            rocChart.dispose();
        }
        rocChart = new Chart(this.rocSash, SWT.NONE);
        rocChart.setOrientation(SWT.HORIZONTAL);
        
        // Show/Hide axis
        rocChart.addControlListener(new ControlAdapter(){
            @Override
            public void controlResized(ControlEvent arg0) {
                updateCategories(rocChart);
            }
        });

        // Update font
        FontData[] fd = rocChart.getFont().getFontData();
        fd[0].setHeight(8);
        final Font font = new Font(rocChart.getDisplay(), fd[0]);
        rocChart.setFont(font);
        rocChart.addDisposeListener(new DisposeListener(){
            public void widgetDisposed(DisposeEvent arg0) {
                if (font != null && !font.isDisposed()) {
                    font.dispose();
                }
            } 
        });
        
        // Update title
        ITitle graphTitle = rocChart.getTitle();
        graphTitle.setText(""); //$NON-NLS-1$
        graphTitle.setFont(rocChart.getFont());
        
        // Set colors
        rocChart.setBackground(rocRoot.getBackground());
        rocChart.setForeground(rocRoot.getForeground());
        
        // OSX workaround
        if (System.getProperty("os.name").toLowerCase().contains("mac")){ //$NON-NLS-1$ //$NON-NLS-2$
            int r = rocChart.getBackground().getRed()-13;
            int g = rocChart.getBackground().getGreen()-13;
            int b = rocChart.getBackground().getBlue()-13;
            r = r>0 ? r : 0;
            r = g>0 ? g : 0;
            r = b>0 ? b : 0;
            final Color background = new Color(rocChart.getDisplay(), r, g, b);
            rocChart.setBackground(background);
            rocChart.addDisposeListener(new DisposeListener(){
                public void widgetDisposed(DisposeEvent arg0) {
                    if (background != null && !background.isDisposed()) {
                        background.dispose();
                    }
                } 
            });
        }

        // Initialize axes
        IAxisSet axisSet = rocChart.getAxisSet();
        IAxis yAxis = axisSet.getYAxis(0);
        IAxis xAxis = axisSet.getXAxis(0);
        ITitle xAxisTitle = xAxis.getTitle();
        xAxisTitle.setText(""); //$NON-NLS-1$
        xAxis.getTitle().setFont(rocChart.getFont());
        yAxis.getTitle().setFont(rocChart.getFont());
        xAxis.getTick().setFont(rocChart.getFont());
        yAxis.getTick().setFont(rocChart.getFont());
        xAxis.getTick().setForeground(rocChart.getForeground());
        yAxis.getTick().setForeground(rocChart.getForeground());
        xAxis.getTitle().setForeground(rocChart.getForeground());
        yAxis.getTitle().setForeground(rocChart.getForeground());

        // Initialize axes
        ITitle yAxisTitle = yAxis.getTitle();
        yAxisTitle.setText(Resources.getMessage("ViewStatisticsClassificationInput.19")); //$NON-NLS-1$
        xAxisTitle.setText(Resources.getMessage("ViewStatisticsClassificationInput.20")); //$NON-NLS-1$
        rocChart.setEnabled(false);
        updateCategories(rocChart);
    }

    /**
     * Updates the chart with a new ROC Curve
     * 
     * @param data
     */
    private void rocSetChartSeries(ROCCurve[] data) {
        
        ROCCurve baseline = data[0];
        ROCCurve output = data[1];
        ROCCurve original = data[2];

        // Init data
        rocChart.setRedraw(false);

        ISeriesSet seriesSet = rocChart.getSeriesSet();
        ISeries[] seriesArray = seriesSet.getSeries();
        // Clear set
        for (ISeries s : seriesArray) {
            rocChart.getSeriesSet().deleteSeries(s.getId());
        }

        // Baseline (ZeroR)
        ILineSeries seriesZeror = (ILineSeries) seriesSet.createSeries(SeriesType.LINE, 
                                                                  Resources.getMessage("ViewStatisticsClassificationInput.12")); // $NON-NLS-1$
        seriesZeror.getLabel().setVisible(false);
        seriesZeror.getLabel().setFont(rocChart.getFont());
        seriesZeror.setLineColor(Display.getDefault().getSystemColor(SWT.COLOR_BLUE));
        seriesZeror.setXSeries(baseline.getFalsePositiveRate());
        seriesZeror.setYSeries(baseline.getTruePositiveRate());
        seriesZeror.setAntialias(SWT.ON);
        seriesZeror.setSymbolType(PlotSymbolType.NONE);
        seriesZeror.enableArea(false);

        // Output
        if (output != null) {
            ILineSeries seriesOutput = (ILineSeries) seriesSet.createSeries(SeriesType.LINE, 
                                                          Resources.getMessage("ViewStatisticsClassificationInput.26")); // $NON-NLS-1$
            seriesOutput.getLabel().setVisible(false);
            seriesOutput.getLabel().setFont(rocChart.getFont());
            seriesOutput.setLineColor(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
            seriesOutput.setXSeries(output.getFalsePositiveRate());
            seriesOutput.setYSeries(output.getTruePositiveRate());
            seriesOutput.setAntialias(SWT.ON);
            seriesOutput.setSymbolType(PlotSymbolType.NONE);
            seriesOutput.enableArea(false);
        }

        // Original
        ILineSeries series = (ILineSeries) seriesSet.createSeries(SeriesType.LINE, isOutput ? Resources.getMessage("ViewStatisticsClassificationInput.25") : Resources.getMessage("ViewStatisticsClassificationInput.26")); // $NON-NLS-1$
        series.getLabel().setVisible(false);
        series.getLabel().setFont(rocChart.getFont());
        series.setLineColor(Display.getDefault().getSystemColor(SWT.COLOR_RED));
        series.setXSeries(original.getFalsePositiveRate());
        series.setYSeries(original.getTruePositiveRate());
        series.setAntialias(SWT.ON);
        series.setSymbolType(PlotSymbolType.NONE);
        series.enableArea(false);

        rocChart.getLegend().setVisible(true);
        rocChart.getLegend().setPosition(SWT.TOP);

        IAxisSet axisSet = rocChart.getAxisSet();
        IAxis yAxis = axisSet.getYAxis(0);
        yAxis.setRange(new Range(0d, 1d));
        IAxis xAxis = axisSet.getXAxis(0);
        xAxis.setRange(new Range(0d, 1d));
        xAxis.adjustRange();

        rocChart.setRedraw(true);
        rocChart.updateLayout();
        rocChart.update();
        rocChart.redraw();
    }
    
    /**
     * Makes the chart show category labels or not.
     */
    private void updateCategories(Chart chart){
        if (chart != null){
            IAxisSet axisSet = chart.getAxisSet();
            if (axisSet != null) {
                IAxis xAxis = axisSet.getXAxis(0);
                if (xAxis != null) {
                    String[] series = xAxis.getCategorySeries();
                    if (series != null) {
                        boolean enoughSpace = chart.getPlotArea().getSize().x / series.length >= MIN_CATEGORY_WIDTH;
                        xAxis.enableCategory(enoughSpace);
                        xAxis.getTick().setVisible(enoughSpace);
                    }
                }
            }
        }
    }

    /**
     * Updates the performance per class table
     * @param target variable
     */
    private void updateOverview(String target) {
        
        
        performancePerClassTable.setRedraw(false);
        for (final TableItem i : performancePerClassTable.getItems()) {
            i.dispose();
        }
        performancePerClassTable.setRedraw(true);

        if(!originalRocCurves.containsKey(target)) {
            return;
        }
        
        // Create entries
        List<String> values = new ArrayList<>(originalRocCurves.get(target).keySet());
        Collections.sort(values);
        
        // Prepare
        List<Double> sensitivities = new ArrayList<Double>();
        List<Double> specificities = new ArrayList<Double>();
        List<Double> brierscores = new ArrayList<Double>();
        
        // For each class
        for (String clazz : values) {

            ROCCurve c;
            if (isOutput) {
                c = rocCurves.get(target).get(clazz);
            } else {
                c = originalRocCurves.get(target).get(clazz);
            }

            // Create entry
            TableItem item = new TableItem(performancePerClassTable, SWT.NONE);
            item.setText(0, clazz);
            item.setData("1", c.getSensitivity());
            item.setData("2", c.getSpecificity());
            item.setData("3", c.getBrierScore());
            
            // Collect measurements
            sensitivities.add(c.getSensitivity());
            specificities.add(c.getSpecificity());
            brierscores.add(c.getBrierScore());
        }  
        
        // Prepare
        double[] min = new double[3];
        double[] avg = new double[3];
        double[] max = new double[3];
        
        // Determine aggregates
        for (int i = 0; i < sensitivities.size(); i++) {
            double sensitivity = sensitivities.get(i);
            min[0] = min[0]==0d ? sensitivity : Math.min(min[0], sensitivity);
            max[0] = Math.max(max[0], sensitivity);
            avg[0] += sensitivity;

            double specificity = specificities.get(i);
            min[1] = min[1]==0d ? specificity : Math.min(min[1], specificity);
            max[1] = Math.max(max[1], specificity);
            avg[1] += specificity;

            double brierscore = brierscores.get(i);
            min[2] = min[2]==0d ? brierscore:  Math.min(min[2], brierscore);
            max[2] = Math.max(max[2], brierscore);
            avg[2] += brierscore;
        }
        
        // Minimum
        TableItem item = new TableItem(performancePerClassTable, SWT.NONE);
        item.setText(0, Resources.getMessage("ViewStatisticsClassificationInput.7"));
        item.setData("1", min[0]);
        item.setData("2", min[1]);
        item.setData("3", min[2]);

        // Average
        item = new TableItem(performancePerClassTable, SWT.NONE);
        item.setText(0, Resources.getMessage("ViewStatisticsClassificationInput.6"));
        item.setData("1", avg[0] / values.size());
        item.setData("2", avg[1] / values.size());
        item.setData("3", avg[2] / values.size());

        // Maximum
        item = new TableItem(performancePerClassTable, SWT.NONE);
        item.setText(0, Resources.getMessage("ViewStatisticsClassificationInput.4"));
        item.setData("1", max[0]);
        item.setData("2", max[1]);
        item.setData("3", max[2]);
    }
    
    
    /**
     * Update the performance overview on selection.
     */
    private void updateOverviewSelection(String attribute) {
        
        // Update table
        int index = 0;
        for (TableItem item : performancePerTargetTable.getItems()) {
            if (item.getText(0).equals(attribute)) {
                performancePerTargetTable.select(index);
                updateOverview(attribute);
                break;
            }
            index++;
        }
    }


    /**
     * Updates classes and corresponding AUC values in table and sets roc curve 
     * data at each table item according to this target variable.
     * 
     * @param targetVariable
     * @param clazz class value
     */
    private void updateROC(String targetVariable, String clazz){
        
        // Check
        if (originalRocCurves.isEmpty() || !originalRocCurves.containsKey(targetVariable)) {
            return;
        }
        
        // Reset chart
        for (final TableItem i : rocTable.getItems()) {
            i.dispose();
        }
        
        // Collect classes
        List<String> classes = new ArrayList<>(originalRocCurves.get(targetVariable).keySet());
        Collections.sort(classes);
        for(String value : classes){
            
            // Class
            TableItem item = new TableItem(rocTable, SWT.NONE);
            item.setText(0, value);
            ROCCurve[] data = {null, null, null};
            item.setData(data);
            
            // Baseline AUC
            ROCCurve rocZeror = zerorRocCurves.get(targetVariable).get(value);
            item.setData("1", rocZeror.getAUC());
            data[0] = rocZeror;
            
            // Original AUC
            ROCCurve rocOriginal = originalRocCurves.get(targetVariable).get(value);
            item.setData(isOutput ? "3" : "2", rocOriginal.getAUC());
            data[2] = rocOriginal;

            // Output
            if (isOutput) {
                
                // AUC (anonymized)
                ROCCurve rocOutput = rocCurves.get(targetVariable).get(value);
                item.setData("2", rocOutput.getAUC());
                data[1] = rocOutput;

                // Relative AUC
                double relativeAUC;
                if(rocOriginal.getAUC() - rocZeror.getAUC() == 0d) {
                    relativeAUC = rocOutput.getAUC() / rocZeror.getAUC();
                } else {
                    relativeAUC = (rocOutput.getAUC() - rocZeror.getAUC()) / (rocOriginal.getAUC() - rocZeror.getAUC());
                }
                item.setData("4", relativeAUC);
            }
        }
        
        // Select according class
        rocTable.setFocus();
        int index = 0;
        for (int i=0; i<rocTable.getItemCount(); i++) {
            if (rocTable.getItem(i).getText().equals(clazz)) {
                index = i;
                break;
            }
        }
        rocTable.select(index);
    }

    /**
     * Updates the combo box to the selected target variable, the roc table with
     * according auc values and the chart according to the selected class.
     */
    private void updateROCSelection(String classValue) {
        
        // Update combo
        String selectedTarget = getModel().getSelectedAttribute();
        for (int i = 0; i < rocCombo.getItemCount(); i++) {
            if (rocCombo.getItem(i).equals(selectedTarget)) {
                rocCombo.select(i);
                updateROC(selectedTarget, classValue);
                break;
            }
        }

        // Update table
        int index = 0;
        for (TableItem item : rocTable.getItems()) {
            if (item.getText(0).equals(classValue)) {
                rocTable.select(index);
                if (item.getData() != null) {
                    rocSetChartSeries((ROCCurve[]) item.getData());
                }
                break;
            }
            index++;
        }
    }

    @Override
    protected Control createControl(Composite parent) {

        // Create top composite
        Composite root = new Composite(parent, SWT.NONE);
        root.setLayout(new FillLayout());
        ComponentTitledFolderButtonBar bar = new ComponentTitledFolderButtonBar();

        // Add update button
        bar.add(Resources.getMessage("ViewStatisticsClassificationInput.29"), getController().getResources().getManagedImage("arrow_refresh.png"), new Runnable(){ //$NON-NLS-1$ //$NON-NLS-2$ 
            public void run() {
                triggerUpdate();
            }
        });
        
        this.folder = new ComponentTitledFolder(root, null, bar, null, true, false);
        
        
        // Performance overview
        Composite item1 = folder.createItem(Resources.getMessage("ViewStatisticsClassificationInput.27"), //$NON-NLS-1$
                                            getController().getResources().getManagedImage("precision_recall.png")); //$NON-NLS-1$
        item1.setLayoutData(SWTUtil.createFillGridData());
        this.createOverviewControl(item1);
        
        // Roc
        Composite item2 = folder.createItem(Resources.getMessage("ViewStatisticsClassificationInput.28"), //$NON-NLS-1$
                                            getController().getResources().getManagedImage("roc.png")); //$NON-NLS-1$
        item2.setLayoutData(SWTUtil.createFillGridData());
        this.createROCControl(item2);
        
        // Synchronize
        this.folder.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent arg0) {
                if (other != null) {
                    other.setSelectionIndex(folder.getSelectionIndex());
                }
            }
        });
        
        // Return
        return root;
    }
    
    @Override
    protected AnalysisContextClassification createViewConfig(AnalysisContext context) {
        return new AnalysisContextClassification(context);
    }
    
    @Override
    protected void doReset() {
        
        // Manager
        if (this.manager != null) {
            this.manager.stop();
        }
        
        // Performance overview
        performancePerTargetTable.setRedraw(false);
        for (final TableItem i : performancePerTargetTable.getItems()) {
            i.dispose();
        }
        performancePerTargetTable.setRedraw(true);
        performancePerClassTable.setRedraw(false);
        for (final TableItem i : performancePerClassTable.getItems()) {
            i.dispose();
        }
        performancePerClassTable.setRedraw(true);
        
        // ROC
        rocTable.setRedraw(false);
        for (final TableItem i : rocTable.getItems()) {
            i.dispose();
        }
        rocTable.setRedraw(true);
        if (rocCombo != null && rocCombo.getItemCount() != 0) rocCombo.select(0);
        if (rocCurves != null) {
            rocCurves.clear();
        }
        if (originalRocCurves != null) {
            originalRocCurves.clear();
        }
        if (zerorRocCurves != null) {
            zerorRocCurves.clear();
        }
        
        resetChart();
        
        // Reset view
        setStatusEmpty();
    }

    @Override
    protected void doUpdate(final AnalysisContextClassification context) {

        // The statistics builder
        final StatisticsBuilderInterruptible builder = context.handle.getStatistics().getInterruptibleInstance();
        final String[] features = context.model.getSelectedFeatures().toArray(new String[0]);
        final String[] targetVariables = context.model.getSelectedClasses().toArray(new String[0]);
        final ARXClassificationConfiguration<?> config = context.model.getClassificationModel().getCurrentConfiguration();
        final ARXFeatureScaling scaling = context.model.getClassificationModel().getFeatureScaling();
        
        // Break, if nothing do
        if (context.model.getSelectedFeatures().isEmpty() ||
            context.model.getSelectedClasses().isEmpty()) {
            doReset();
            return;
        }
        
        // Create an analysis
        Analysis analysis = new Analysis(){

            private boolean                     stopped    = false;
            private List<List<Double>>          values     = new ArrayList<>();
            private int                         progress   = 0;

            @Override
            public int getProgress() {
                
                double result = 0d;
                double perBatch = 100d / (double)targetVariables.length;
                result += (double)progress * perBatch;
                result += (double)builder.getProgress() / 100d * perBatch;
                result = result <= 100d ? result : 100d;
                return (int)result;
            }
            
            @Override
            public void onError() {
                rocCurves.clear();
                originalRocCurves.clear();
                zerorRocCurves.clear();
                setStatusEmpty();
            }

            @Override
            public void onFinish() {

                // Check
                if (stopped || !isEnabled() || getModel().getSelectedFeatures().isEmpty() || getModel().getSelectedClasses().isEmpty()) {
                    setStatusEmpty();
                    return;
                }

                // Update chart
                for (final TableItem i : performancePerTargetTable.getItems()) {
                    i.dispose();
                }

                // Create entries
                for (int i = 0; i < targetVariables.length; i++) {
                    TableItem item = new TableItem(performancePerTargetTable, SWT.NONE);
                    item.setText(0, targetVariables[i]);
                    for (int j = 0; j < values.get(i).size(); j++) {
                        item.setData(String.valueOf(1 + j), values.get(i).get(j));
                    }
                }

                // Update overview
                String targetVariable = getModel().getSelectedAttribute();
                int targetIndex = getIndexOf(targetVariables, targetVariable);
                performancePerTargetTable.setFocus();
                performancePerTargetTable.select(targetIndex);
                updateOverview(performancePerTargetTable.getItem(targetIndex).getText());
                performanceRoot.layout();
                performanceSash.setWeights(new int[] {2, 2});
                
                // Update combo box
                rocCombo.setItems(targetVariables);
                rocCombo.select(targetIndex);
                
                // Layout
                rocRoot.layout();
                rocSash.setWeights(new int[] {2, 2});
                setStatusDone();
                
                // Select first element in folder
                folder.setSelection(0);
                
                // Update overview
                if (performancePerTargetTable.getItemCount() != 0) {
                    updateOverviewSelection(performancePerTargetTable.getItem(targetIndex).getText());
                }
                if (performancePerClassTable.getItemCount() != 0) {
                    updateROCSelection(performancePerClassTable.getItem(0).getText(0));
                }
            }

            @Override
            public void onInterrupt() {
                if (!isEnabled() || getModel().getSelectedFeatures().isEmpty() || getModel().getSelectedClasses().isEmpty()) {
                    setStatusEmpty();
                } else {
                    setStatusWorking();
                }
            }

            @Override
            public void run() throws InterruptedException {
                
                // Timestamp
                long time = System.currentTimeMillis();

                // Clear
                rocCurves.clear();
                originalRocCurves.clear();
                zerorRocCurves.clear();
                
                // Do work
                for (String targetVariable : targetVariables) {
                    
                    // Compute
                    StatisticsClassification result = builder.getClassificationPerformance(features,
                                                                                           targetVariable,
                                                                                           config,
                                                                                           scaling);
                    progress++;
                    if (stopped) {
                        break;
                    }
                    
                    // Collect performance data
                    values.add(getColumnValuesForOverallPerformanceTable(result));

                    // Collect ROC curves
                    if(!originalRocCurves.containsKey(targetVariable)){
                        originalRocCurves.put(targetVariable, new HashMap<String, ROCCurve>());
                        zerorRocCurves.put(targetVariable, new HashMap<String, ROCCurve>());
                        rocCurves.put(targetVariable, new HashMap<String, ROCCurve>());
                    }
                    for (String c : result.getClassValues()) {
                        originalRocCurves.get(targetVariable).put(c, result.getOriginalROCCurve(c));
                        zerorRocCurves.get(targetVariable).put(c, result.getZeroRROCCurve(c));
                        if (result.getROCCurve(c) != null) {
                            rocCurves.get(targetVariable).put(c, result.getROCCurve(c));
                        }
                    }
                }

                // Our users are patient
                while (System.currentTimeMillis() - time < MINIMAL_WORKING_TIME && !stopped){
                    Thread.sleep(10);
                }
            }

            @Override
            public void stop() {
                builder.interrupt();
                this.stopped = true;
            }
        };
        
        this.manager.start(analysis);
    }
    
    /**
     * Returns all column headers for the AUC table
     * @return
     */
    protected abstract String[] getColumnHeadersForAUCTable();

    /**
     * Returns all column headers for the overall performance table
     * @return
     */
    protected abstract String[] getColumnHeadersForPerformanceForOverallPerformanceTable();
    
    /**
     * Returns all values for one row of the overall performance table
     * @param result
     * @return
     */
    protected abstract List<Double> getColumnValuesForOverallPerformanceTable(StatisticsClassification result);
    
    @Override
    protected ComponentStatusLabelProgressProvider getProgressProvider() {
        return new ComponentStatusLabelProgressProvider(){
            public int getProgress() {
                if (manager == null) {
                    return 0;
                } else {
                    return manager.getProgress();
                }
            }
        };
    }

    /**
     * Is an analysis running
     */
    protected boolean isRunning() {
        return manager != null && manager.isRunning();
    }

    /**
     * Sets the other view for synchronization
     * @param other
     */
    protected void setOtherView(ViewStatisticsClassification other) {
        this.other = other;
    }

    /**
     * Sets the selection index in the underlying folder
     * @param index
     */
    protected void setSelectionIndex(int index) {
        this.folder.setSelection(index);
    }
}
