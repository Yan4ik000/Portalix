package dev.portalix.client.config;

import static dev.portalix.portal.PortalViewLimits.MAX_PREVIEW_RADIUS;
import static dev.portalix.portal.PortalViewLimits.MIN_PREVIEW_RADIUS;

import dev.portalix.client.PortalixClient;
import dev.portalix.client.compat.minecraft.GuiAccess;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

public final class PortalixSettingsScreen extends Screen {
  private final Screen parent;
  private HeaderAndFooterLayout layout;
  private boolean showCrosshair;
  private int ownDistance;
  private int otherDistance;
  private boolean ambientSound;

  public PortalixSettingsScreen(Screen parent) {
    super(Component.translatable("options.portalix.title"));
    this.parent = parent;
    var settings = PortalixClientSettings.get();
    showCrosshair = settings.showPortalCrosshair();
    ownDistance = settings.ownPortalRenderDistance();
    otherDistance = settings.otherPortalRenderDistance();
    ambientSound = settings.portalAmbientSound();
  }

  @Override
  protected void init() {
    layout = new HeaderAndFooterLayout(this);
    layout.addTitleHeader(title, font);
    int controlWidth = Math.min(310, width - 24);
    var options = layout.addToContents(LinearLayout.vertical().spacing(4));
    options.defaultCellSetting().alignHorizontallyCenter();
    options.addChild(CycleButton.onOffBuilder(showCrosshair).create(
        0, 0, controlWidth, 20, Component.translatable("options.portalix.crosshair"),
        (button, enabled) -> showCrosshair = enabled));
    options.addChild(new DistanceSlider(controlWidth, "options.portalix.own_portal_distance", ownDistance,
        distance -> ownDistance = distance));
    options.addChild(new DistanceSlider(controlWidth, "options.portalix.other_portal_distance", otherDistance,
        distance -> otherDistance = distance));
    var warning = Component.translatable("options.portalix.distance_warning").withColor(CommonColors.LIGHT_GRAY);
    options.addChild(new MultiLineTextWidget(warning, font).setMaxWidth(controlWidth).setCentered(true));
    options.addChild(CycleButton.onOffBuilder(ambientSound).create(
        0, 0, controlWidth, 20, Component.translatable("options.portalix.portal_ambient_sound"),
        (button, enabled) -> ambientSound = enabled));
    layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> save()).width(200).build());
    layout.visitWidgets(this::addRenderableWidget);
    repositionElements();
  }

  @Override
  protected void repositionElements() {
    layout.arrangeElements();
  }

  @Override
  public void onClose() {
    GuiAccess.setScreen(minecraft, parent);
  }

  private void save() {
    var settings = new PortalixClientSettings(showCrosshair, ownDistance, otherDistance, ambientSound);
    if (!PortalixClientSettings.save(settings)) {
      GuiAccess.setScreen(minecraft, new AlertScreen(() -> GuiAccess.setScreen(minecraft, this),
          title, Component.translatable("options.portalix.save_error"), CommonComponents.GUI_BACK, false));
      return;
    }
    if (!ambientSound) PortalixClient.portals().stopAmbient();
    onClose();
  }

  private static final class DistanceSlider extends AbstractSliderButton {
    private static final int RANGE = MAX_PREVIEW_RADIUS - MIN_PREVIEW_RADIUS;
    private final Component label;
    private final IntConsumer change;

    DistanceSlider(int width, String label, int distance, IntConsumer change) {
      super(0, 0, width, DEFAULT_HEIGHT, CommonComponents.EMPTY, (double) (distance - MIN_PREVIEW_RADIUS) / RANGE);
      this.label = Component.translatable(label);
      this.change = change;
      updateMessage();
    }

    private int distance() {
      return MIN_PREVIEW_RADIUS + (int) Math.round(value * RANGE);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
      if (canChangeValue && (event.isLeft() || event.isRight())) {
        int nextDistance = distance() + (event.isLeft() ? -1 : 1);
        setValue((double) (nextDistance - MIN_PREVIEW_RADIUS) / RANGE);
        return true;
      }
      return super.keyPressed(event);
    }

    @Override
    protected void updateMessage() {
      setMessage(CommonComponents.optionNameValue(label, Component.literal(Integer.toString(distance()))));
    }

    @Override
    protected void applyValue() {
      change.accept(distance());
    }
  }
}
