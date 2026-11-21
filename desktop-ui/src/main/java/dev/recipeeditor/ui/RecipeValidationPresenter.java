package dev.recipeeditor.ui;

import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.core.ResourceLocation;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Groups repeated diagnostics without changing their severity or losing actual shaped-slot positions. */
public final class RecipeValidationPresenter {
    public String format(final RecipeEditPreview preview) {
        final var grouped=new LinkedHashMap<ValidationIssue,Integer>();preview.validation().issues().forEach(issue->grouped.merge(issue,1,Integer::sum));
        final var lines=new ArrayList<String>();
        grouped.forEach((issue,count)->{
            final var slots=new ArrayList<Integer>();
            if(issue.field().equals("ingredient"))for(int i=0;i<preview.grid().size();i++) {
                final var ids=new HashSet<ResourceLocation>();collect(RecipeSlotContent.parse(preview.grid().get(i)),ids);
                if(ids.stream().anyMatch(id->Pattern.compile("(?<![a-z0-9_:/.-])"+Pattern.quote(id.toString())+"(?![a-z0-9_:/.-])").matcher(issue.message()).find()))slots.add(i+1);
            }
            lines.add(issue.severity()+" ["+issue.field()+"] "+issue.message()
                    +(slots.isEmpty()?(count>1?"\nOccurrences: "+count:""):"\nAffected slots: "+slots.stream().map(Object::toString).collect(Collectors.joining(", "))));
        });
        return String.join("\n\n",lines);
    }
    private void collect(final RecipeSlotContent value,final Set<ResourceLocation> ids){if(value.id()!=null)ids.add(value.id());value.alternatives().forEach(child->collect(child,ids));}
}
