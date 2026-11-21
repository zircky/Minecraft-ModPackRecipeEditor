package dev.recipeeditor.ui;

import java.util.TreeSet;
import javafx.beans.property.IntegerProperty;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Callback;

/** Accessible presentation of the existing JavaFX page model; no record slicing lives here. */
public final class PaginationView extends BorderPane {
    private final Pagination model=new Pagination(1);
    private final FlowPane controls=new FlowPane();
    private final Label summary=new Label();
    private final TextField destination=new TextField();
    private Callback<Integer,Node> factory;
    private int pageCount;

    public PaginationView() {
        getStyleClass().add("pagination-view");controls.getStyleClass().add("pagination-controls");
        destination.setPromptText("Page");destination.setPrefColumnCount(4);destination.setMaxWidth(80);
        destination.setAccessibleText("Go to page number");
        destination.setOnAction(event->goToPage());
        final var footer=new VBox(8,controls,summary);footer.getStyleClass().add("pagination-footer");setBottom(footer);
        model.currentPageIndexProperty().addListener((o,a,b)->refresh());refresh();
    }
    public IntegerProperty currentPageIndexProperty(){return model.currentPageIndexProperty();}
    public int getCurrentPageIndex(){return model.getCurrentPageIndex();}
    public int getPageCount(){return pageCount;}
    public void setCurrentPageIndex(final int value){model.setCurrentPageIndex(pageCount==0?0:Math.clamp(value,0,pageCount-1));}
    public void setPageCount(final int count) {
        if(count<0)throw new IllegalArgumentException("Negative page count");
        pageCount=count;model.setPageCount(Math.max(1,count));setCurrentPageIndex(model.getCurrentPageIndex());refresh();
    }
    public void setPageFactory(final Callback<Integer,Node> factory){this.factory=factory;refresh();}
    private void goToPage() {
        try {
            final int number=Integer.parseInt(destination.getText().strip());
            if(number<1||number>pageCount)throw new NumberFormatException();
            setCurrentPageIndex(number-1);destination.clear();destination.setAccessibleHelp("");destination.setTooltip(null);
        } catch(NumberFormatException invalid) {
            destination.setAccessibleHelp("Enter a page number between 1 and "+pageCount);
            destination.setTooltip(new Tooltip(destination.getAccessibleHelp()));
        }
    }
    private void refresh() {
        final int current=model.getCurrentPageIndex();
        if(factory!=null)setCenter(factory.call(current));
        controls.getChildren().clear();
        controls.getChildren().addAll(action("«","First page",0,current==0),action("‹","Previous page",current-1,current==0));
        final var visible=new TreeSet<Integer>();
        if(pageCount>0){visible.add(0);visible.add(pageCount-1);for(int i=Math.max(0,current-1);i<=Math.min(pageCount-1,current+1);i++)visible.add(i);}
        int previous=-1;
        for(final int index:visible) {
            if(previous>=0&&index>previous+1)controls.getChildren().add(new Label("…"));
            final var number=new ToggleButton(Integer.toString(index+1));number.getStyleClass().add("page-number");
            number.setSelected(index==current);number.setAccessibleText("Page "+(index+1));
            number.setOnAction(event->setCurrentPageIndex(index));controls.getChildren().add(number);previous=index;
        }
        controls.getChildren().addAll(action("›","Next page",current+1,current>=pageCount-1),action("»","Last page",pageCount-1,current>=pageCount-1),destination);
        destination.setDisable(pageCount==0);summary.setText(pageCount==0?"No pages":"Page "+(current+1)+" of "+pageCount);
    }
    private Button action(final String text,final String name,final int index,final boolean disabled) {
        final var button=new Button(text);button.setAccessibleText(name);button.setTooltip(new Tooltip(name));
        button.setDisable(pageCount==0||disabled);button.setOnAction(event->setCurrentPageIndex(index));return button;
    }
}
