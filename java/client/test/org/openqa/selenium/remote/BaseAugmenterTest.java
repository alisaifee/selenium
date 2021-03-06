/*
Copyright 2007-2010 Selenium committers

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.openqa.selenium.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.openqa.selenium.remote.CapabilityType.SUPPORTS_BROWSER_CONNECTION;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Rotatable;
import org.openqa.selenium.ScreenOrientation;
import org.openqa.selenium.StubDriver;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseAugmenterTest {

  @Test
  public void shouldReturnANormalWebDriverUntouched() {
    WebDriver driver = new StubDriver();

    WebDriver returned = getAugmenter().augment(driver);

    assertSame(driver, returned);
  }

  @Test
  public void shouldLeaveARemoteWebDriverWhichCannotTakeSnapshotsAlone() throws Exception {
    DesiredCapabilities caps = new DesiredCapabilities();
    WebDriver driver = new RemoteWebDriver(new StubExecutor(caps), caps);

    WebDriver returned = getAugmenter().augment(driver);
    System.out.println("This is the returned webdriver " + returned);
    assertSame(driver, returned);
    assertFalse(returned instanceof TakesScreenshot);
  }

  @Test
  public void shouldAddTheTakesSnapshotInterfaceIfNecessary() {
    DesiredCapabilities caps = new DesiredCapabilities();
    caps.setCapability(CapabilityType.TAKES_SCREENSHOT, true);
    WebDriver driver = new RemoteWebDriver(new StubExecutor(caps), caps);

    WebDriver returned = getAugmenter().augment(driver);

    assertNotSame(driver, returned);
    assertTrue(returned instanceof TakesScreenshot);
  }

  @Test
  public void shouldNotAddTheTakesSnapshotInterfaceWhenBooleanValueIsFalse() {
    DesiredCapabilities caps = new DesiredCapabilities();
    caps.setCapability(CapabilityType.TAKES_SCREENSHOT, false);
    WebDriver driver = new RemoteWebDriver(new StubExecutor(caps), caps);

    WebDriver returned = getAugmenter().augment(driver);

    assertSame(driver, returned);
    assertFalse(returned instanceof TakesScreenshot);
  }

  @Test
  public void shouldDelegateToHandlerIfAdded() {
    DesiredCapabilities caps = new DesiredCapabilities();
    caps.setCapability("foo", true);

    BaseAugmenter augmenter = getAugmenter();
    augmenter.addDriverAugmentation("foo", new AugmenterProvider() {
      public Class<?> getDescribedInterface() {
        return MyInterface.class;
      }

      public InterfaceImplementation getImplementation(Object value) {
        return new InterfaceImplementation() {
          public Object invoke(ExecuteMethod executeMethod, Object self, Method method,
              Object... args) {
            return "Hello World";
          }
        };
      }
    });

    WebDriver driver = new RemoteWebDriver(new StubExecutor(caps), caps);
    WebDriver returned = augmenter.augment(driver);

    String text = ((MyInterface) returned).getHelloWorld();
    assertEquals("Hello World", text);
  }

  @Test
  public void shouldDelegateUnmatchedMethodCallsToDriverImplementation() {
    DesiredCapabilities caps = new DesiredCapabilities();
    caps.setCapability(CapabilityType.TAKES_SCREENSHOT, true);
    StubExecutor stubExecutor = new StubExecutor(caps);
    stubExecutor.expect(DriverCommand.GET_TITLE, new HashMap<String, Object>(), "Title");
    WebDriver driver = new RemoteWebDriver(stubExecutor, caps);

    WebDriver returned = getAugmenter().augment(driver);

    assertEquals("Title", returned.getTitle());
  }

  @Test(expected = NoSuchElementException.class)
  public void proxyShouldNotAppearInStackTraces() {
    final DesiredCapabilities caps = new DesiredCapabilities();
    // This will force the class to be enhanced
    caps.setCapability(CapabilityType.TAKES_SCREENSHOT, true);

    DetonatingDriver driver = new DetonatingDriver();
    driver.setCapabilities(caps);

    WebDriver returned = getAugmenter().augment(driver);

    returned.findElement(By.id("ignored"));
  }


  @Test
  public void shouldLeaveAnUnAugmentableElementAlone() {
    RemoteWebElement element = new RemoteWebElement();
    element.setId("1234");

    WebElement returned = getAugmenter().augment(element);

    assertSame(element, returned);
  }

  @Test
  public void shouldAllowAnElementToBeAugmented() throws Exception {
    RemoteWebElement element = new RemoteWebElement();
    element.setId("1234");

    BaseAugmenter augmenter = getAugmenter();
    augmenter.addElementAugmentation("foo", new AugmenterProvider() {
      public Class<?> getDescribedInterface() {
        return MyInterface.class;
      }

      public InterfaceImplementation getImplementation(Object value) {
        return new InterfaceImplementation() {
          public Object invoke(ExecuteMethod executeMethod, Object self, Method method,
              Object... args) {
            return "Hello World";
          }
        };
      }
    });

    final DesiredCapabilities caps = new DesiredCapabilities();
    caps.setCapability("foo", true);

    StubExecutor executor = new StubExecutor(caps);
    RemoteWebDriver parent = new RemoteWebDriver(executor, caps) {
      @Override
      public Capabilities getCapabilities() {
        return caps;
      }
    };
    element.setParent(parent);

    WebElement returned = augmenter.augment(element);

    assertTrue(returned instanceof MyInterface);

    executor.expect(DriverCommand.CLICK_ELEMENT, ImmutableMap.of("id", "1234"),
        null);
    returned.click();
  }

  @Test
  public void shouldCopyFieldsFromTemplateInstanceIntoChildInstance() {
    ChildRemoteDriver driver = new ChildRemoteDriver();
    driver.setMagicNumber(3);
    MagicNumberHolder holder = (MagicNumberHolder) getAugmenter().augment(driver);

    assertEquals(3, holder.getMagicNumber());
  }

  @Test
  public void shouldNotChokeOnFinalFields() {
    WithFinals withFinals = new WithFinals();
    try {
      getAugmenter().augment(withFinals);
    } catch (Exception e) {
      fail("This is not expected: " + e.getMessage());
    }
  }


  @Test
  public void shouldBeAbleToAugmentMultipleTimes() {
    DesiredCapabilities caps = new DesiredCapabilities();
    caps.setCapability("canRotate", true);
    caps.setCapability("reallyTakesScreenshot", true);

    StubExecutor stubExecutor = new StubExecutor(caps);
    stubExecutor.expect(DriverCommand.GET_SCREEN_ORIENTATION,
        ImmutableMap.<String, Object>of(),
        ScreenOrientation.PORTRAIT.name());
    RemoteWebDriver driver = new RemoteWebDriver(stubExecutor, caps);

    BaseAugmenter augmenter = getAugmenter();
    augmenter.addDriverAugmentation("canRotate", new AddRotatable());

    WebDriver augmented = augmenter.augment(driver);
    assertNotSame(augmented, driver);
    assertTrue(augmented instanceof Rotatable);
    assertFalse(augmented instanceof TakesScreenshot);

    augmenter = getAugmenter();
    augmenter.addDriverAugmentation("reallyTakesScreenshot", new AddTakesScreenshot());

    WebDriver augmentedAgain = augmenter.augment(augmented);
    assertNotSame(augmentedAgain, augmented);
    assertTrue(augmentedAgain instanceof Rotatable);
    assertTrue(augmentedAgain instanceof TakesScreenshot);

    ((Rotatable) augmentedAgain).getOrientation();  // Should not throw.

    assertSame(driver.getCapabilities(),
        ((HasCapabilities) augmentedAgain).getCapabilities());
  }

  protected static class StubExecutor implements CommandExecutor {
    private final Capabilities capabilities;
    private final List<Data> expected = Lists.newArrayList();

    protected StubExecutor(Capabilities capabilities) {
      this.capabilities = capabilities;
    }

    public Response execute(Command command) {
      if (DriverCommand.NEW_SESSION.equals(command.getName())) {
        Response response = new Response(new SessionId("foo"));
        response.setValue(capabilities.asMap());
        return response;
      }

      for (Data possibleMatch : expected) {
        if (possibleMatch.commandName.equals(command.getName()) &&
            possibleMatch.args.equals(command.getParameters())) {
          Response response = new Response(new SessionId("foo"));
          response.setValue(possibleMatch.returnValue);
          return response;
        }
      }

      fail("Unexpected method invocation: " + command);
      return null; // never reached
    }

    public void expect(String commandName, Map<String, ?> args, Object returnValue) {
      expected.add(new Data(commandName, args, returnValue));
    }

    private static class Data {
      public String commandName;
      public Map<String, ?> args;
      public Object returnValue;

      public Data(String commandName, Map<String, ?> args, Object returnValue) {
        this.commandName = commandName;
        this.args = args;
        this.returnValue = returnValue;
      }
    }
  }

  public interface MyInterface {
    String getHelloWorld();
  }

  public static class DetonatingDriver extends RemoteWebDriver {
    private Capabilities caps;

    public void setCapabilities(Capabilities caps) {
      this.caps = caps;
    }

    @Override
    public Capabilities getCapabilities() {
      return caps;
    }

    @Override
    public WebElement findElementById(String id) {
      throw new NoSuchElementException("Boom");
    }
  }

  private interface MagicNumberHolder {
    public int getMagicNumber();
    public void setMagicNumber(int number);
  }

  public static class ChildRemoteDriver extends RemoteWebDriver implements MagicNumberHolder {
    private int magicNumber;

    @Override
    public Capabilities getCapabilities() {
      DesiredCapabilities caps = DesiredCapabilities.firefox();
      caps.setCapability(SUPPORTS_BROWSER_CONNECTION, true);
      return caps;
    }

    @Override
    public int getMagicNumber() {
      return magicNumber;
    }

    @Override
    public void setMagicNumber(int magicNumber) {
      this.magicNumber = magicNumber;
    }
  }

  public static class WithFinals extends RemoteWebDriver {
    public final String finalField = "FINAL";

    @Override
    public Capabilities getCapabilities() {
      return new DesiredCapabilities();
    }
  }

  public abstract BaseAugmenter getAugmenter();
}
