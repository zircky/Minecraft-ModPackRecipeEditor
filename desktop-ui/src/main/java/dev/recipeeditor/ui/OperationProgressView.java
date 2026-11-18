package dev.recipeeditor.ui;

import dev.recipeeditor.core.importing.ImportProgress;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
final class OperationProgressView extends VBox {
    private final Label stage=new Label();
    private final Label count=new Label();
    private final ProgressBar progress=new ProgressBar(0);
    OperationProgressView(final String title,final String directory,final Runnable cancel) {
        super(18); setPadding(new Insets(40));
        final var heading=new Label(title); heading.getStyleClass().add("empty-title");
        final var path=new Label(directory); path.setWrapText(true); progress.setMaxWidth(Double.MAX_VALUE);
        final var button=new Button("Cancel"); button.setOnAction(e->cancel.run());
        getChildren().addAll(heading,path,stage,progress,count,button);
    }
    void update(final ImportProgress value) { stage.setText(value.stage()); progress.setProgress(value.fraction()); count.setText(value.total()>0?"Processed: "+value.processed()+" / "+value.total():""); }
}
