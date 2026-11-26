package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.integrations.gregtech.GregTechMachineCatalog.Machine;
import java.util.*;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

final class GregTechMachineSelector extends VBox {
    GregTechMachineSelector(final List<Machine> machines, final ResourceLocation selected,
            final Consumer<Machine> onChange, final boolean editable) {
        super(5);
        final var search=new TextField();
        search.setPromptText("Search discovered machine types");
        final var rows=new FilteredList<>(FXCollections.observableArrayList(machines));
        final var combo=new ComboBox<>(rows);
        combo.setId("gt-machine-type");
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setCellFactory(list->machineCell());
        combo.setButtonCell(machineCell());
        final var details=new Label();
        details.setWrapText(true);
        final var apply=new Button("Use machine type");
        apply.setDisable(true);
        combo.valueProperty().addListener((observable,old,value)->{
            apply.setDisable(!editable||value==null||value.id().equals(selected));
            details.setText(value==null?"No machine selected.":value.id()+" · Source: "+value.sourceMod()
                    +"\n"+(value.limits().isEmpty()?"No verified limits in this snapshot":new TreeMap<>(value.limits()).toString())
                    +(editable?"":"\nInspection only for this project/adapter."));
        });
        search.textProperty().addListener((observable,old,value)->{
            final String query=value.strip().toLowerCase(Locale.ROOT);
            rows.setPredicate(machine->machine.id().toString().contains(query)||machine.label().toLowerCase(Locale.ROOT).contains(query));
        });
        machines.stream().filter(machine->machine.id().equals(selected)).findFirst().ifPresent(combo::setValue);
        apply.setOnAction(event->{if(combo.getValue()!=null)onChange.accept(combo.getValue());});
        getChildren().addAll(new Label("Machine Recipe Type"),search,combo,details);
        if(editable)getChildren().add(apply);
    }

    private ListCell<Machine> machineCell() {
        return new ListCell<>() {
            @Override protected void updateItem(final Machine machine,final boolean empty) {
                super.updateItem(machine,empty);
                setText(empty||machine==null?null:machine.label());
                setTooltip(empty||machine==null?null:new Tooltip(machine.id().toString()));
            }
        };
    }
}
