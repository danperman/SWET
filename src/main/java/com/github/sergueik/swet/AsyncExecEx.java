package com.github.sergueik.swet;
/**
 * Copyright 2014 - 2017 Serguei Kouzmine
 */

import java.util.Formatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Category;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.By;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

// origin: 
// http://www.java2s.com/Code/JavaAPI/org.eclipse.swt.widgets/DisplayasyncExecRunnablerun.htm
// http://www.java2s.com/Code/Java/SWT-JFace-Eclipse/SWTandThread.htm
// TODO:
// https://github.com/Holzschneider/Swinger/blob/master/src/de/dualuse/commons/swing/AnimatedImageIcon.java
// https://github.com/Holzschneider/Sweater/blob/develop/src/de/dualuse/swt/graphics/BufferedImageData.java
// https://github.com/Holzschneider/Sweater/blob/develop/src/de/dualuse/swt/widgets/InputDialog.java
public class AsyncExecEx {

	final static int MIN_PERCENTAGE = 0;
	final static int MAX_PERCENTAGE = 30;
	final static int DELAY = 10000;

	private static WebDriver driver;
	private WebDriverWait wait;
	private static int flexibleWait = 5;
	private static int implicitWait = 1;
	private static long pollingInterval = 500;

	private static ProgressBar progressBar;
	private static Button launchButton;
	private static Button collectButton;
	private static Button asyncButton;
	private static Thread longRunningOperation;
	private static Utils utils = Utils.getInstance();

	@SuppressWarnings("deprecation")
	static final Category logger = Category.getInstance(SimpleToolBarEx.class);
	private static StringBuilder loggingSb = new StringBuilder();
	private static Formatter formatter = new Formatter(loggingSb, Locale.US);

	public static void main(String[] a) {
		Display display = new Display();
		Shell shell = new Shell(display);
		shell.setLayout(new GridLayout());

		launchButton = new Button(shell, SWT.CENTER);
		launchButton.setText("Launch browser");
		launchButton.setBounds(shell.getClientArea());
		launchButton.addListener(SWT.Selection, event -> {
			launchButton.setEnabled(false);
			collectButton.setEnabled(false);
			String browser = "chrome";
			driver = BrowserDriver.initialize(browser);
			driver.manage().timeouts().pageLoadTimeout(50, TimeUnit.SECONDS)
					.implicitlyWait(implicitWait, TimeUnit.SECONDS)
					.setScriptTimeout(30, TimeUnit.SECONDS);
			String baseURL = "https://www.google.com";
			driver.get(baseURL);
			launchButton.setEnabled(true);
			collectButton.setEnabled(true);
		});

		collectButton = new Button(shell, SWT.CENTER);
		collectButton.setText("Inject script");
		collectButton.setBounds(shell.getClientArea());
		// TODO: https://stackoverflow.com/questions/13479833/java-swt-animated-gif
		collectButton
				.addSelectionListener(new AsyncDataCollectionListener(collectButton));

		shell.setSize(300, 200);
		shell.open();
		int percentage = MIN_PERCENTAGE;
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	static class LongRunningOperation extends Thread {
		private Display display;
		private Shell parentShell = null;
		private ProgressBar progressBar;
		private Button button;
		private int percentage;

		public LongRunningOperation(Display display, ProgressBar progressBar,
				Button button) {
			this.display = display;
			this.progressBar = progressBar;
			this.parentShell = progressBar.getShell();
			this.button = button;
		}

		public void run() {
			utils.setDriver(driver);
			utils.setFlexibleWait(flexibleWait);
			utils.injectElementSearch(Optional.<String> empty());

			for (percentage = MIN_PERCENTAGE; percentage <= MAX_PERCENTAGE; percentage++) {
				try {
					Thread.sleep(DELAY);
				} catch (InterruptedException e) {
				}
				String payload = utils.getPayload();
				// simplified
				if (!payload.isEmpty()) {
					System.err.println(payload);
					if (payload.contains((CharSequence) "ElementCodeName")) {
						System.err.println("Trying to close");
						utils.flushVisualSearchResult();
						utils.closeVisualSearch();
					}
				}

			}
			display.asyncExec(new Runnable() {
				public void run() {
					if (percentage == MIN_PERCENTAGE) {
						// NOTE: not reliable
						button.setEnabled(false);
					}
					if (progressBar.isDisposed())
						return;
					progressBar.setSelection(progressBar.getSelection() + 1);
					// progressBar.setSelection(percentage);
					parentShell.setData("percentage", percentage);
					if (percentage == MAX_PERCENTAGE) {
						button.setEnabled(true);
					}
				}
			});
			return;
		}
	}

	// http://www.cyberforum.ru/java-gui/thread893423.html
	// http://www.java2s.com/Tutorial/Java/0280__SWT/SWTTimeConsumingOperationUIPattern.htm

	private static class AsyncDataCollectionListener
			implements SelectionListener {
		private Button parent;
		private Boolean waitingForData = false;

		AsyncDataCollectionListener(Button parent) {
			this.parent = parent;
		}

		public void widgetSelected(SelectionEvent e) {
			Thread thread = new Thread(new Runnable() {
				@Override
				public void run() {
					parent.getDisplay().asyncExec(new Runnable() {
						@Override
						public void run() {
							parent.setEnabled(false);
						}
					});
					waitingForData = true;
					logger.info("Started");
					try {
						utils.setDriver(driver);
						utils.setFlexibleWait(flexibleWait);
						utils.injectElementSearch(Optional.<String> empty());
					} catch (Exception e) {
						waitingForData = false;
					}
					while (waitingForData) {
						String payload = utils.getPayload();
						// simplified
						if (!payload.isEmpty()) {
							System.err.println(payload);
							if (payload.contains((CharSequence) "ElementCodeName")) {
								System.err.println("Trying to close");
								utils.flushVisualSearchResult();
								utils.closeVisualSearch();
								waitingForData = false;
							}
						}
						if (waitingForData) {
							try {
								// TODO: add the alternative code to
								// bail if waited long enough already
								logger.info("Waiting.");
								Thread.sleep(1000);
							} catch (InterruptedException exception) {
							}
						}
					}
					logger.info("Finished");
					parent.getDisplay().asyncExec(new Runnable() {
						@Override
						public void run() {
							parent.setEnabled(true);
						}
					});

				}
			});
			thread.start();
		}

		public void widgetDefaultSelected(SelectionEvent e) {
		}
	}
}
