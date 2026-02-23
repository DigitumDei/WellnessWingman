using OpenQA.Selenium;
using OpenQA.Selenium.Appium;
using OpenQA.Selenium.Appium.Android;
using OpenQA.Selenium.Interactions;
using OpenQA.Selenium.Support.UI;

namespace WellnessWingman.UITests.PageObjects;

/// <summary>
/// Base class for all page objects providing common functionality
/// </summary>
public abstract class BasePage
{
    protected readonly AndroidDriver Driver;
    protected readonly WebDriverWait Wait;

    protected BasePage(AndroidDriver driver)
    {
        Driver = driver;
        Wait = new WebDriverWait(driver, TimeSpan.FromSeconds(10));
    }

    /// <summary>
    /// Finds an element by AutomationId (accessible via AccessibilityId in Appium)
    /// </summary>
    protected AppiumElement? FindByAutomationId(string automationId)
    {
        try
        {
            return Driver.FindElement(MobileBy.AccessibilityId(automationId));
        }
        catch (NoSuchElementException)
        {
            return null;
        }
    }

    /// <summary>
    /// Finds an element by text content
    /// </summary>
    protected AppiumElement? FindByText(string text)
    {
        try
        {
            return Driver.FindElement(MobileBy.AndroidUIAutomator(
                $"new UiSelector().text(\"{text}\")"));
        }
        catch (NoSuchElementException)
        {
            return null;
        }
    }

    /// <summary>
    /// Finds an element by partial text content
    /// </summary>
    protected AppiumElement? FindByPartialText(string partialText)
    {
        try
        {
            return Driver.FindElement(MobileBy.AndroidUIAutomator(
                $"new UiSelector().textContains(\"{partialText}\")"));
        }
        catch (NoSuchElementException)
        {
            return null;
        }
    }

    /// <summary>
    /// Finds a button by its text
    /// </summary>
    protected AppiumElement? FindButtonByText(string text)
    {
        try
        {
            return Driver.FindElement(MobileBy.AndroidUIAutomator(
                $"new UiSelector().className(\"android.widget.Button\").text(\"{text}\")"));
        }
        catch (NoSuchElementException)
        {
            return null;
        }
    }

    /// <summary>
    /// Waits for an element to be visible
    /// </summary>
    protected AppiumElement WaitForElement(By locator, int timeoutSeconds = 10)
    {
        var wait = new WebDriverWait(Driver, TimeSpan.FromSeconds(timeoutSeconds));
        return (AppiumElement)wait.Until(driver =>
        {
            try
            {
                var element = driver.FindElement(locator);
                return element.Displayed ? element : null;
            }
            catch (NoSuchElementException)
            {
                return null;
            }
        })!;
    }

    /// <summary>
    /// Waits for an element with AutomationId to be visible
    /// </summary>
    protected AppiumElement? WaitForAutomationId(string automationId, int timeoutSeconds = 10)
    {
        try
        {
            return WaitForElement(MobileBy.AccessibilityId(automationId), timeoutSeconds);
        }
        catch (WebDriverTimeoutException)
        {
            return null;
        }
    }

    /// <summary>
    /// Checks if an element exists (without waiting)
    /// </summary>
    protected bool ElementExists(By locator)
    {
        return Driver.FindElements(locator).Count > 0;
    }

    /// <summary>
    /// Checks if text is visible on the page
    /// </summary>
    protected bool IsTextVisible(string text)
    {
        return FindByText(text)?.Displayed ?? false;
    }

    /// <summary>
    /// Taps on an element
    /// </summary>
    protected void Tap(AppiumElement element)
    {
        element.Click();
    }

    /// <summary>
    /// Enters text into an element
    /// </summary>
    protected void EnterText(AppiumElement element, string text)
    {
        element.Clear();
        element.SendKeys(text);
    }

    /// <summary>
    /// Swipes from right to left (navigate forward)
    /// </summary>
    protected void SwipeLeft()
    {
        var size = Driver.Manage().Window.Size;
        var startX = (int)(size.Width * 0.8);
        var endX = (int)(size.Width * 0.2);
        var y = size.Height / 2;

        PerformSwipe(startX, y, endX, y);
    }

    /// <summary>
    /// Swipes from left to right (navigate back)
    /// </summary>
    protected void SwipeRight()
    {
        var size = Driver.Manage().Window.Size;
        var startX = (int)(size.Width * 0.2);
        var endX = (int)(size.Width * 0.8);
        var y = size.Height / 2;

        PerformSwipe(startX, y, endX, y);
    }

    /// <summary>
    /// Scrolls down on the page
    /// </summary>
    protected void ScrollDown()
    {
        var size = Driver.Manage().Window.Size;
        var startY = (int)(size.Height * 0.8);
        var endY = (int)(size.Height * 0.2);
        var x = size.Width / 2;

        PerformSwipe(x, startY, x, endY);
    }

    /// <summary>
    /// Scrolls up on the page
    /// </summary>
    protected void ScrollUp()
    {
        var size = Driver.Manage().Window.Size;
        var startY = (int)(size.Height * 0.2);
        var endY = (int)(size.Height * 0.8);
        var x = size.Width / 2;

        PerformSwipe(x, startY, x, endY);
    }

    /// <summary>
    /// Performs a swipe gesture using W3C Actions API
    /// </summary>
    private void PerformSwipe(int startX, int startY, int endX, int endY)
    {
        var finger = new PointerInputDevice(PointerKind.Touch);
        var sequence = new ActionSequence(finger);

        sequence.AddAction(finger.CreatePointerMove(CoordinateOrigin.Viewport, startX, startY, TimeSpan.Zero));
        sequence.AddAction(finger.CreatePointerDown(MouseButton.Left));
        sequence.AddAction(finger.CreatePointerMove(CoordinateOrigin.Viewport, endX, endY, TimeSpan.FromMilliseconds(500)));
        sequence.AddAction(finger.CreatePointerUp(MouseButton.Left));

        Driver.PerformActions(new[] { sequence });
    }
}
