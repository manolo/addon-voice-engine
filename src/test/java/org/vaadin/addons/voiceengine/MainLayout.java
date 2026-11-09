package org.vaadin.addons.voiceengine;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;

@Layout
public class MainLayout extends AppLayout {

    public MainLayout() {
        addToNavbar(new DrawerToggle(), new H1("Voice Engine demos") {{
            getStyle().setFontSize("var(--aura-font-size-l, 1.25rem)").setMargin("0");
        }});
        addToDrawer(new SideNav() {{
            addItem(new SideNavItem("Dictation & read aloud", DictationView.class));
            addItem(new SideNavItem("Low-level API", LowLevelApiView.class));
            addItem(new SideNavItem("1.x/2.x toolbar replica", LegacyToolbarView.class));
        }});
    }
}
