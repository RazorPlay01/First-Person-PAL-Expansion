package com.github.razorplay01.fp_pal.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.github.razorplay01.fp_pal.firstperson.FpAnimationResources;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.Identifier;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Suggestion provider for FP animation commands.
 * Provides autocompletion for FP animation IDs.
 */
public class FpAnimationArgumentProvider<S> implements SuggestionProvider<S> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        List<String> suggestions = new LinkedList<>();
        for (Identifier animation : FpAnimationResources.getAnimations().keySet()) {
            suggestions.add(animation.toString());
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
    }
}
