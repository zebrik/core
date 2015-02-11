package org.jboss.as.console.client.domain.runtime;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.SplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ProvidesKey;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.gwtplatform.mvp.client.ViewImpl;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.core.message.Message;
import org.jboss.as.console.client.domain.model.Server;
import org.jboss.as.console.client.plugins.RuntimeExtensionMetaData;
import org.jboss.as.console.client.plugins.RuntimeExtensionRegistry;
import org.jboss.as.console.client.plugins.RuntimeGroup;
import org.jboss.as.console.client.shared.model.SubsystemRecord;
import org.jboss.as.console.client.v3.stores.domain.actions.FilterType;
import org.jboss.as.console.client.v3.stores.domain.actions.SelectServer;
import org.jboss.as.console.client.widgets.nav.v3.NavigationColumn;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * @author Heiko Braun
 */
public class DomainRuntimeView extends ViewImpl implements DomainRuntimePresenter.MyView {

    private final SplitLayoutPanel splitlayout;
    private  Widget subsysColWidget;
    private LayoutPanel contentCanvas;
    private NavigationColumn<Server> serverColumn;
    private DomainRuntimePresenter presenter;
    private NavigationColumn<ActionLink> statusColumn;
    private NavigationColumn<PlaceLink> subsystemColumn;

    private List<Predicate> metricPredicates = new ArrayList<Predicate>();
    private List<Predicate> runtimePredicates = new ArrayList<Predicate>();
    private List<ActionLink> statusLinks = new ArrayList<ActionLink>();
    private List<SubsystemRecord> subsystems;
    private Widget statusColWidget;

    Stack<Widget> visibleColumns = new Stack<>();

    interface ServerTemplate extends SafeHtmlTemplates {
        @Template("<div class=\"{0}\">{1}&nbsp;<span style='font-size:8px'>({2})</span></div>")
        SafeHtml item(String cssClass, String server, String host);
    }

    interface SubsystemTemplate extends SafeHtmlTemplates {
        @Template("<div class=\"{0}\">{1}</span></div>")
        SafeHtml item(String cssClass, String server);
    }

    private static final ServerTemplate SERVER_TEMPLATE = GWT.create(ServerTemplate.class);

    private static final SubsystemTemplate SUBSYSTEM_TEMPLATE = GWT.create(SubsystemTemplate.class);

    @Inject
    public DomainRuntimeView() {
        super();
        contentCanvas = new LayoutPanel();
        splitlayout = new SplitLayoutPanel(2);

        PlaceLink datasources = new PlaceLink("Datasources", NameTokens.DataSourceMetricPresenter);
        PlaceLink jmsQueues = new PlaceLink("JMS Destinations", NameTokens.JmsMetricPresenter);
        PlaceLink web = new PlaceLink("Web", NameTokens.WebMetricPresenter);
        PlaceLink jpa = new PlaceLink("JPA", NameTokens.JPAMetricPresenter);
        PlaceLink ws = new PlaceLink("Webservices", NameTokens.WebServiceRuntimePresenter);
        PlaceLink naming = new PlaceLink("JNDI View", NameTokens.JndiPresenter);

        metricPredicates.add(new Predicate("datasources", datasources));
        metricPredicates.add(new Predicate("messaging", jmsQueues));
        metricPredicates.add(new Predicate("web", web));
        metricPredicates.add(new Predicate("jpa", jpa));
        metricPredicates.add(new Predicate("webservices", ws));
        metricPredicates.add(new Predicate("naming", naming));

        // Extension based additions
        RuntimeExtensionRegistry registry = Console.getRuntimeLHSItemExtensionRegistry();
        List<RuntimeExtensionMetaData> menuExtensions = registry.getExtensions();
        for (RuntimeExtensionMetaData ext : menuExtensions) {

            if(RuntimeGroup.METRICS.equals(ext.getGroup()))
            {
                metricPredicates.add(
                        new Predicate(
                                ext.getKey(), new PlaceLink(ext.getName(), ext.getToken())
                        )
                );
            }
            else if(RuntimeGroup.OPERATiONS.equals(ext.getGroup()))
            {
                runtimePredicates.add(
                        new Predicate(
                                ext.getKey(), new PlaceLink(ext.getName(), ext.getToken())
                        )
                );
            }
            else
            {
                Log.warn("Invalid runtime group for extension: " + ext.getGroup());
            }
        }

        // default links
        statusLinks.add(new ActionLink("JVM", new Command() {
            @Override
            public void execute() {
                reduceColumnsTo(2);
                // NameTokens.HostVMMetricPresenter
            }
        }));
        statusLinks.add(new ActionLink("Environment", new Command() {
            @Override
            public void execute() {
                reduceColumnsTo(2);
                // NameTokens.EnvironmentPresenter
            }
        }));
        statusLinks.add(new ActionLink("Log Files", new Command() {
            @Override
            public void execute() {
                reduceColumnsTo(2);
                // NameTokens.LogFiles
            }
        }));


        statusLinks.add(new ActionLink("Subsystems", new Command() {
            @Override
            public void execute() {
                reduceColumnsTo(2);
                appendColumn(subsysColWidget);
                updateSubsystemColumn(subsystems);
            }
        }));

    }

    @Override
    public Widget asWidget() {



        serverColumn = new NavigationColumn<Server>(
                "Server",
                new NavigationColumn.Display<Server>() {
                    @Override
                    public SafeHtml render(String baseCss, Server data) {
                        String context = presenter.getFilter().equals(FilterType.HOST) ? data.getGroup() : data.getHostName();
                        return SERVER_TEMPLATE.item(baseCss, data.getName(), context);
                    }
                },
                new ProvidesKey<Server>() {
                    @Override
                    public Object getKey(Server item) {
                        return item.getName() + item.getHostName();
                    }
                });


        statusColumn = new NavigationColumn<ActionLink>(
                "Status",
                new NavigationColumn.Display<ActionLink>() {
                    @Override
                    public SafeHtml render(String baseCss, ActionLink data) {
                        return SUBSYSTEM_TEMPLATE.item(baseCss, data.getTitle());
                    }
                },
                new ProvidesKey<ActionLink>() {
                    @Override
                    public Object getKey(ActionLink item) {
                        return item.getTitle();
                    }
                });

        statusColWidget = statusColumn.asWidget();

        subsystemColumn = new NavigationColumn<PlaceLink>(
                "Subsystems",
                new NavigationColumn.Display<PlaceLink>() {
                    @Override
                    public SafeHtml render(String baseCss, PlaceLink data) {
                        return SUBSYSTEM_TEMPLATE.item(baseCss, data.getTitle());
                    }
                },
                new ProvidesKey<PlaceLink>() {
                    @Override
                    public Object getKey(PlaceLink item) {
                        return item.getTitle();
                    }
                });


        subsysColWidget = subsystemColumn.asWidget();

        // server column is always present
        appendColumn(serverColumn.asWidget());

        // selection handling

        serverColumn.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(SelectionChangeEvent event) {

                if (serverColumn.hasSelectedItem()) {

                    final Server selectedServer = serverColumn.getSelectedItem();

                    reduceColumnsTo(1);
                    appendColumn(statusColWidget);
                    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
                        public void execute() {

                            Console.getCircuit().dispatch(
                                    new SelectServer(selectedServer.getHostName(), selectedServer.getName())
                            );
                        }
                    });
                }
            }
        });

        statusColumn.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(SelectionChangeEvent event) {

                if (statusColumn.hasSelectedItem()) {

                    final ActionLink selectedLink = statusColumn.getSelectedItem();
                     selectedLink.getCmd().execute();
                }
            }
        });


        subsystemColumn.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(SelectionChangeEvent event) {

                if (subsystemColumn.hasSelectedItem()) {

                    final PlaceLink selectedLink = subsystemColumn.getSelectedItem();

                  /*  Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
                        public void execute() {

                            Console.getPlaceManager().revealPlace(new PlaceRequest(selectedLink.getToken()));
                        }
                    });*/
                }
            }
        });


        return splitlayout.asWidget();
    }

    private void appendColumn(Widget columnWidget) {
        visibleColumns.push(columnWidget);
        splitlayout.addWest(columnWidget, 217);
    }

    private void reduceColumnsTo(int level) {

        for(int i=visibleColumns.size()-1; i>=level; i--)
        {
            Widget widget = visibleColumns.pop();
            boolean b = splitlayout.remove(widget);
            System.out.println(i+" popped? "+b);
        }
    }


    @Override
    public void setInSlot(Object slot, IsWidget  content) {
        if (slot == DomainRuntimePresenter.TYPE_MainContent) {
            if(content!=null)
                setContent(content);

        } else {
            Console.MODULES.getMessageCenter().notify(
                    new Message("Unknown slot requested:" + slot)
            );
        }
    }

    private void setContent(IsWidget  newContent) {
        contentCanvas.clear();
        contentCanvas.add(newContent);
    }

    @Override
    public void setPresenter(DomainRuntimePresenter presenter) {
        this.presenter = presenter;
    }


    @Override
    public void updateServerList(List<Server> serverModel) {
        reduceColumnsTo(1);
        serverColumn.updateFrom(serverModel, false);
    }

    private class PlaceLink {

        private String title;
        private String token;

        public PlaceLink(String title, String token) {
            this.title = title;
            this.token = token;
        }

        public String getTitle() {
            return title;
        }

        public String getToken() {
            return token;
        }
    }

    private class ActionLink {

        private String title;
        private Command cmd;

        public ActionLink(String title, Command cmd) {
            this.title = title;
            this.cmd = cmd;
        }

        public String getTitle() {
            return title;
        }

        public Command getCmd() {
            return cmd;
        }
    }

    public final class Predicate {
        private String subsysName;
        private PlaceLink link;

        public Predicate(String subsysName, PlaceLink navItem) {
            this.subsysName = subsysName;
            this.link = navItem;
        }

        public boolean matches(String current) {
            return current.equals(subsysName);
        }

        public PlaceLink getLink() {
            return link;
        }
    }

    @Override
    public void setSubsystems(List<SubsystemRecord> subsystems) {

        statusColumn.updateFrom(statusLinks);

        this.subsystems = subsystems;
    }

    private void updateSubsystemColumn(List<SubsystemRecord> subsystems)
    {
        List<PlaceLink> runtimeLinks = new ArrayList<>();

        for(SubsystemRecord subsys : subsystems)
        {

            for(Predicate predicate : metricPredicates)
            {
                if(predicate.matches(subsys.getKey()))
                    runtimeLinks.add(predicate.getLink());
            }
        }

        subsystemColumn.updateFrom(runtimeLinks, false);
    }

}
