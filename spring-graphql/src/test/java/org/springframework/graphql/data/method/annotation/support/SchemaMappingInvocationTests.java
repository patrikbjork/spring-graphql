/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.graphql.data.method.annotation.support;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.web.ProjectedPayload;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookCriteria;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.RequestInput;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test GraphQL requests handled through {@code @SchemaMapping} methods.
 *
 * @author Rossen Stoyanchev
 * @author Mark Paluch
 */
public class SchemaMappingInvocationTests {

	@Test
	void queryWithScalarArgument() {
		String query = "{ " +
				"  bookById(id:\"1\") { " +
				"    id" +
				"    name" +
				"    author {" +
				"      firstName" +
				"      lastName" +
				"    }" +
				"  }" +
				"}";

		Mono<ExecutionResult> resultMono = graphQlService().execute(new RequestInput(query, null, null, null, "1"));

		Book book = GraphQlResponse.from(resultMono).toEntity("bookById", Book.class);
		assertThat(book.getId()).isEqualTo(1);
		assertThat(book.getName()).isEqualTo("Nineteen Eighty-Four");

		Author author = book.getAuthor();
		assertThat(author.getFirstName()).isEqualTo("George");
		assertThat(author.getLastName()).isEqualTo("Orwell");
	}

	@Test
	void queryWithObjectArgument() {
		String query = "{ " +
				"  booksByCriteria(criteria: {author:\"Orwell\"}) { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		Mono<ExecutionResult> resultMono = graphQlService().execute(new RequestInput(query, null, null, null, "1"));

		List<Book> bookList = GraphQlResponse.from(resultMono).toList("booksByCriteria", Book.class);
		assertThat(bookList).hasSize(2);
		assertThat(bookList.get(0).getName()).isEqualTo("Nineteen Eighty-Four");
		assertThat(bookList.get(1).getName()).isEqualTo("Animal Farm");
	}

	@Test
	void queryWithProjectedArgument() {
		String query = "{ " +
				"  booksByProjectedArguments(author:\"Orwell\") { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		Mono<ExecutionResult> resultMono = graphQlService().execute(new RequestInput(query, null, null, null, "1"));

		List<Book> bookList = GraphQlResponse.from(resultMono).toList("booksByProjectedArguments", Book.class);
		assertThat(bookList).hasSize(2);
		assertThat(bookList.get(0).getName()).isEqualTo("Nineteen Eighty-Four");
		assertThat(bookList.get(1).getName()).isEqualTo("Animal Farm");
	}

	@Test
	void booksByProjectedCriteria() {
		String query = "{ " +
				"  booksByProjectedCriteria(criteria: {author:\"Orwell\"}) { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		Mono<ExecutionResult> resultMono = graphQlService().execute(new RequestInput(query, null, null, null, "1"));

		List<Book> bookList = GraphQlResponse.from(resultMono).toList("booksByProjectedCriteria", Book.class);
		assertThat(bookList).hasSize(2);
		assertThat(bookList.get(0).getName()).isEqualTo("Nineteen Eighty-Four");
		assertThat(bookList.get(1).getName()).isEqualTo("Animal Farm");
	}

	@Test
	void queryWithArgumentViaDataFetchingEnvironment() {
		String query = "{ " +
				"  authorById(id:\"101\") { " +
				"    id" +
				"    firstName" +
				"    lastName" +
				"  }" +
				"}";

		AtomicReference<GraphQLContext> contextRef = new AtomicReference<>();
		RequestInput requestInput = new RequestInput(query, null, null, null, "1");
		requestInput.configureExecutionInput((executionInput, builder) -> {
			contextRef.set(executionInput.getGraphQLContext());
			return executionInput;
		});

		Mono<ExecutionResult> resultMono = graphQlService().execute(requestInput);

		Author author = GraphQlResponse.from(resultMono).toEntity("authorById", Author.class);
		assertThat(author.getId()).isEqualTo(101);
		assertThat(author.getFirstName()).isEqualTo("George");
		assertThat(author.getLastName()).isEqualTo("Orwell");

		assertThat(contextRef.get().<String>get("key")).isEqualTo("value");
	}

	@Test
	void mutation() {
		String operation = "mutation { " +
				"  addAuthor(firstName:\"James\", lastName:\"Joyce\") { " +
				"    id" +
				"    firstName" +
				"    lastName" +
				"  }" +
				"}";

		Mono<ExecutionResult> resultMono = graphQlService()
				.execute(new RequestInput(operation, null, null, null, "1"));

		Author author = GraphQlResponse.from(resultMono).toEntity("addAuthor", Author.class);
		assertThat(author.getId()).isEqualTo(99);
		assertThat(author.getFirstName()).isEqualTo("James");
		assertThat(author.getLastName()).isEqualTo("Joyce");
	}

	@Test
	void subscription() {
		String operation = "subscription { " +
				"  bookSearch(author:\"Orwell\") { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		Mono<ExecutionResult> resultMono = graphQlService()
				.execute(new RequestInput(operation, null, null, null, "1"));

		Flux<Book> bookFlux = GraphQlResponse.forSubscription(resultMono)
				.map(response -> response.toEntity("bookSearch", Book.class));

		StepVerifier.create(bookFlux)
				.consumeNextWith(book -> {
					assertThat(book.getId()).isEqualTo(1);
					assertThat(book.getName()).isEqualTo("Nineteen Eighty-Four");
				})
				.consumeNextWith(book -> {
					assertThat(book.getId()).isEqualTo(5);
					assertThat(book.getName()).isEqualTo("Animal Farm");
				})
				.verifyComplete();
	}


	private GraphQlService graphQlService() {
		BatchLoaderRegistry registry = new DefaultBatchLoaderRegistry();

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(BookController.class);
		context.registerBean(BatchLoaderRegistry.class, () -> registry);
		context.refresh();

		return GraphQlSetup.schemaResource(BookSource.schema)
				.runtimeWiringForAnnotatedControllers(context)
				.dataLoaders(registry)
				.toGraphQlService();
	}


	@SuppressWarnings("unused")
	@Controller
	private static class BookController {

		public BookController(BatchLoaderRegistry batchLoaderRegistry) {
			batchLoaderRegistry.forTypePair(Long.class, Author.class)
					.registerBatchLoader((ids, env) -> Flux.fromIterable(ids).map(BookSource::getAuthor));
		}

		@QueryMapping
		public Book bookById(@Argument Long id) {
			return BookSource.getBookWithoutAuthor(id);
		}

		@QueryMapping
		public List<Book> booksByCriteria(@Argument BookCriteria criteria) {
			return BookSource.findBooksByAuthor(criteria.getAuthor());
		}

		@QueryMapping
		public List<Book> booksByProjectedArguments(BookProjection projection) {
			return BookSource.findBooksByAuthor(projection.getAuthor());
		}

		@QueryMapping
		public List<Book> booksByProjectedCriteria(@Argument BookProjection criteria) {
			return BookSource.findBooksByAuthor(criteria.getAuthor());
		}

		@SchemaMapping
		public CompletableFuture<Author> author(Book book, DataLoader<Long, Author> dataLoader) {
			return dataLoader.load(book.getAuthorId());
		}

		@QueryMapping
		public Author authorById(DataFetchingEnvironment environment, GraphQLContext context) {
			context.put("key", "value");
			String id = environment.getArgument("id");
			return BookSource.getAuthor(Long.parseLong(id));
		}

		@MutationMapping
		public Author addAuthor(@Argument String firstName, @Argument String lastName) {
			return new Author(99L, firstName, lastName);
		}

		@SubscriptionMapping
		public Flux<Book> bookSearch(@Argument String author) {
			return Flux.fromIterable(BookSource.findBooksByAuthor(author));
		}
	}

	@ProjectedPayload
	interface BookProjection {

		String getAuthor();

	}

}
