package com.dotcms.rest.api.v1.contenttype;

import static com.dotcms.util.DotPreconditions.checkNotEmpty;
import static com.dotcms.util.DotPreconditions.checkNotNull;
import static com.liferay.util.StringPool.COMMA;

import com.dotcms.api.web.HttpServletRequestThreadLocal;
import com.dotcms.business.WrapInTransaction;
import com.dotcms.contenttype.business.ContentTypeAPI;
import com.dotcms.contenttype.business.CopyContentTypeBean;
import com.dotcms.contenttype.business.FieldDiffCommand;
import com.dotcms.contenttype.business.FieldDiffItemsKey;
import com.dotcms.contenttype.exception.NotFoundInDbException;
import com.dotcms.contenttype.model.field.Field;
import com.dotcms.contenttype.model.field.FieldVariable;
import com.dotcms.contenttype.model.type.ContentType;
import com.dotcms.contenttype.transform.contenttype.ContentTypeInternationalization;
import com.dotcms.exception.ExceptionUtil;
import com.dotcms.repackage.com.google.common.annotations.VisibleForTesting;
import com.dotcms.rest.InitDataObject;
import com.dotcms.rest.ResponseEntityView;
import com.dotcms.rest.WebResource;
import com.dotcms.rest.annotation.InitRequestRequired;
import com.dotcms.rest.annotation.NoCache;
import com.dotcms.rest.annotation.PermissionsUtil;
import com.dotcms.rest.exception.BadRequestException;
import com.dotcms.rest.exception.ForbiddenException;
import com.dotcms.rest.exception.mapper.ExceptionMapperUtil;
import com.dotcms.util.PaginationUtil;
import com.dotcms.util.diff.DiffItem;
import com.dotcms.util.diff.DiffResult;
import com.dotcms.util.pagination.ContentTypesPaginator;
import com.dotcms.util.pagination.OrderDirection;
import com.dotcms.workflow.form.WorkflowSystemActionForm;
import com.dotcms.workflow.helper.WorkflowHelper;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.DotStateException;
import com.dotmarketing.business.PermissionAPI;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.portlets.workflows.business.WorkflowAPI;
import com.dotmarketing.portlets.workflows.model.SystemActionWorkflowActionMapping;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.PageMode;
import com.dotmarketing.util.UUIDUtil;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.json.JSONException;
import com.dotmarketing.util.json.JSONObject;
import com.google.common.collect.ImmutableMap;
import com.liferay.portal.model.User;
import com.liferay.util.StringPool;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.server.JSONP;

/**
 * This REST Endpoint provides information related to Content Types in the current dotCMS repository.
 *
 * @author Will Ezell
 * @since Sep 11th, 2016
 */
@Path("/v1/contenttype")
@Tag(name = "Content Type")
public class ContentTypeResource implements Serializable {

	private static final String MAP_KEY_WORKFLOWS = "workflows";
	private static final String MAP_KEY_SYSTEM_ACTION_MAPPINGS = "systemActionMappings";

	private final WebResource 		webResource;
	private final ContentTypeHelper contentTypeHelper;
	private final PaginationUtil 	paginationUtil;
	private final WorkflowHelper 	workflowHelper;
	private final PermissionAPI     permissionAPI;

	public ContentTypeResource() {
		this(ContentTypeHelper.getInstance(), new WebResource(),
				new PaginationUtil(new ContentTypesPaginator()),
				WorkflowHelper.getInstance(), APILocator.getPermissionAPI());
	}

	@VisibleForTesting
	public ContentTypeResource(final ContentTypeHelper contentletHelper, final WebResource webresource,
							   final PaginationUtil paginationUtil, final WorkflowHelper workflowHelper,
							   final PermissionAPI permissionAPI) {

		this.webResource       = webresource;
		this.contentTypeHelper = contentletHelper;
		this.paginationUtil    = paginationUtil;
		this.workflowHelper    = workflowHelper;
		this.permissionAPI     = permissionAPI;
	}

	private static final long serialVersionUID = 1L;

	public static final String SELECTED_STRUCTURE_KEY = "selectedStructure";

	@POST
	@Path("/{baseVariableName}/_copy")
	@JSONP
	@NoCache
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces({MediaType.APPLICATION_JSON, "application/javascript"})
	public final Response copyType(@Context final HttpServletRequest req,
								   @Context final HttpServletResponse res,
								   @PathParam("baseVariableName") final String baseVariableName,
								   final CopyContentTypeForm copyContentTypeForm) {

		final InitDataObject initData = this.webResource.init(null, req, res, true, null);
		final User user = initData.getUser();
		Response response;

		try {

			if (null == copyContentTypeForm) {

				return ExceptionMapperUtil.createResponse(null, "The Request needs a POST body");
			}

			Logger.debug(this, ()->String.format("Creating new content type '%s' based from  '%s' ", baseVariableName,  copyContentTypeForm.getName()));
			final HttpSession session = req.getSession(false);

			// Validate input
			final ContentTypeAPI contentTypeAPI = APILocator.getContentTypeAPI(user, true);
			final ContentType type = contentTypeAPI.find(baseVariableName);

			if (null == type || (UtilMethods.isSet(type.id()) && !UUIDUtil.isUUID(type.id()))) {

				return ExceptionMapperUtil.createResponse(null, "ContentType 'id' if set, should be a uuid");
			}

			final ImmutableMap<Object, Object> responseMap = this.copyContentTypeAndDependencies(contentTypeAPI, type, copyContentTypeForm, user);

			// save the last one to the session to be compliant with #13719
			if(null != session) {
				session.removeAttribute(SELECTED_STRUCTURE_KEY);
			}

			response = Response.ok(new ResponseEntityView<>(responseMap)).build();
		} catch (final IllegalArgumentException e) {
			final String errorMsg = String.format("Missing required information when copying Content Type " +
					"'%s': %s", baseVariableName, ExceptionUtil.getErrorMessage(e));
			Logger.error(this, errorMsg, e);
			response = ExceptionMapperUtil.createResponse(null, errorMsg);
		} catch (final DotStateException | DotDataException e) {
			final String errorMsg = String.format("Failed to copy Content Type '%s': %s",
					baseVariableName, ExceptionUtil.getErrorMessage(e));
			Logger.error(this, errorMsg, e);
			response = ExceptionMapperUtil.createResponse(null, errorMsg);
		} catch (final DotSecurityException e) {
			Logger.error(this, String.format("User '%s' does not have permission to copy Content Type " +
					"'%s'", user.getUserId(), baseVariableName), e);
			throw new ForbiddenException(e);
		} catch (final Exception e) {
			final String errorMsg = String.format("An error occurred when copying Content Type " +
					"'%s': %s", baseVariableName, ExceptionUtil.getErrorMessage(e));
			Logger.error(this, errorMsg, e);
			response = ExceptionMapperUtil.createResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
		}

		return response;
	}

	@VisibleForTesting
	public static void setHostAndFolderAsIdentifer (final String folderPathOrIdentifier, final String hostOrId, final User user, final CopyContentTypeBean.Builder builder) {

		Host site = APILocator.systemHost();
		if (null != hostOrId) {

			if (Host.SYSTEM_HOST.equals(hostOrId)) {

				site = APILocator.systemHost();
			} else {

				site = Try.of(() -> UUIDUtil.isUUID(hostOrId) ? APILocator.getHostAPI().find(hostOrId, user, false) :
						APILocator.getHostAPI().findByName(hostOrId, user, false)).getOrElse(APILocator.systemHost());
			}

			builder.host(null == site? APILocator.systemHost().getIdentifier():site.getIdentifier());
		}

		if (null != folderPathOrIdentifier) {

			final Host finalSite = site;
			final String folderId =
					Try.of(() -> APILocator.getFolderAPI().findFolderByPath(folderPathOrIdentifier, finalSite, user, false).getIdentifier()).getOrNull();

			builder.folder(null != folderId ? folderId : folderPathOrIdentifier);
		}
	}

	/**
	 * Copies a Content Type -- along with the new information specified for it -- as well as the
	 * references to the Workflow Schemes that the original type is using.
	 *
	 * @param contentTypeAPI      The {@link ContentTypeAPI} instance to use to save the new
	 *                            Content Type.
	 * @param type                The original {@link ContentType} to copy.
	 * @param copyContentTypeForm The {@link CopyContentTypeForm} containing the new information
	 *                            for the copied type, such as, the new name, new icon, and new
	 *                            Velocity Variable Name.
	 * @param user                The {@link User} executing this action.
	 *
	 * @return An {@link ImmutableMap} containing the data from the new Content Type, its Workflow
	 * Schemes and system action mappings.
	 *
	 * @throws DotDataException     An error occurred when saving the new information.
	 * @throws DotSecurityException The specified User doesn't have the required permissions to
	 *                              perform this action.
	 */
	@WrapInTransaction
	private ImmutableMap<Object, Object> copyContentTypeAndDependencies(final ContentTypeAPI contentTypeAPI, final ContentType type,
																		 final CopyContentTypeForm copyContentTypeForm, final User user)
			throws DotDataException, DotSecurityException {

		final CopyContentTypeBean.Builder builder = new CopyContentTypeBean.Builder()
				.sourceContentType(type).icon(copyContentTypeForm.getIcon()).name(copyContentTypeForm.getName())
				.newVariable(copyContentTypeForm.getVariable());

		setHostAndFolderAsIdentifer(copyContentTypeForm.getFolder(), copyContentTypeForm.getHost(), user, builder);
		final ContentType contentTypeSaved = contentTypeAPI.copyFromAndDependencies(builder.build());

		return ImmutableMap.builder()
				.putAll(contentTypeHelper.contentTypeToMap(
						contentTypeAPI.find(contentTypeSaved.variable()), user))
				.put(MAP_KEY_WORKFLOWS,
						this.workflowHelper.findSchemesByContentType(contentTypeSaved.id(), user))
				.put(MAP_KEY_SYSTEM_ACTION_MAPPINGS,
						this.workflowHelper.findSystemActionsByContentType(contentTypeSaved, user)
								.stream()
						.collect(Collectors.toMap(SystemActionWorkflowActionMapping::getSystemAction, mapping->mapping)))
				.build();
	}

	/**
	 * Creates one or more Content Types specified in the Content Type Form parameter. This allows
	 * users to easily create more than one Content Type in a single request.
	 *
	 * @param req  The current instance of the {@link HttpServletRequest}.
	 * @param res  The current instance of the {@link HttpServletResponse}.
	 * @param form The {@link ContentTypeForm} containing the required information to create the
	 *             Content Type(s).
	 *
	 * @return The JSON response with the Content Type(s) created.
	 *
	 * @throws DotDataException An error occurs when persisting the Content Type(s) in the
	 *                          database.
	 */
	@POST
	@JSONP
	@NoCache
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces({MediaType.APPLICATION_JSON, "application/javascript"})
	public final Response createType(@Context final HttpServletRequest req,
									 @Context final HttpServletResponse res,
									 final ContentTypeForm form)
			throws DotDataException {
		final InitDataObject initData =
				new WebResource.InitBuilder(webResource)
						.requestAndResponse(req, res)
						.requiredBackendUser(false)
						.requiredFrontendUser(false)
						.rejectWhenNoUser(true)
						.init();
		final User user = initData.getUser();
		try {
			checkNotNull(form, "The 'form' parameter is required");
			Logger.debug(this, ()->String.format("Creating Content Type(s): %s", form.getRequestJson()));
			final HttpSession session = req.getSession(false);
			final Iterable<ContentTypeForm.ContentTypeFormEntry> typesToSave = form.getIterable();
			final List<Map<Object, Object>> savedContentTypes = new ArrayList<>();

			for (final ContentTypeForm.ContentTypeFormEntry entry : typesToSave) {
				final ContentType type = contentTypeHelper.evaluateContentTypeRequest(
						entry.contentType, user, true
				);
				final Set<String> workflowsIds = new HashSet<>(entry.workflowsIds);

				if (UtilMethods.isSet(type.id()) && !UUIDUtil.isUUID(type.id())) {
					return ExceptionMapperUtil.createResponse(null, String.format("Content Type ID " +
							"'%s' is either not set, or is not a valid UUID", type.id()));
				}

				final Tuple2<ContentType, List<SystemActionWorkflowActionMapping>>  tuple2 =
						this.saveContentTypeAndDependencies(type, initData.getUser(), workflowsIds,
							form.getSystemActions(), APILocator.getContentTypeAPI(user, true), true);
				final ContentType contentTypeSaved = tuple2._1;
				final ImmutableMap<Object, Object> responseMap = ImmutableMap.builder()
						.putAll(contentTypeHelper.contentTypeToMap(contentTypeSaved, user))
						.put(MAP_KEY_WORKFLOWS,
								this.workflowHelper.findSchemesByContentType(contentTypeSaved.id(),
										initData.getUser()))
						.put(MAP_KEY_SYSTEM_ACTION_MAPPINGS, tuple2._2.stream()
								.collect(Collectors.toMap(SystemActionWorkflowActionMapping::getSystemAction, mapping->mapping)))
						.build();
				savedContentTypes.add(responseMap);
				// save the last one to the session to be compliant with #13719
				if(null != session){
                  session.removeAttribute(SELECTED_STRUCTURE_KEY);
				}
			}
			return Response.ok(new ResponseEntityView<>(savedContentTypes)).build();
		} catch (final IllegalArgumentException e) {
			final String errorMsg = String.format("Missing required information when creating Content Type(s): " +
					"%s", ExceptionUtil.getErrorMessage(e));
			Logger.error(this, errorMsg, e);
			return ExceptionMapperUtil.createResponse(null, errorMsg);
		}catch (final DotStateException | DotDataException e) {
			final String errorMsg = String.format("Failed to create Content Type(s): %s", ExceptionUtil.getErrorMessage(e));
			Logger.error(this, errorMsg, e);
			return ExceptionMapperUtil.createResponse(null, errorMsg);
		} catch (final DotSecurityException e) {
			Logger.error(this, String.format("User '%s' does not have permission to create " +
					"Content Type(s)", user.getUserId()), e);
			throw new ForbiddenException(e);
		} catch (final Exception e) {
			final String errorMsg = String.format("An error occurred when creating Content Type(s): " +
					"%s", ExceptionUtil.getErrorMessage(e));
			Logger.error(this, errorMsg, e);
			return ExceptionMapperUtil.createResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Updates the Content Type based on the given ID or Velocity variable name.
	 *
	 * @param idOrVar The ID or Velocity variable name of the Content Type to update.
	 * @param form    The {@link ContentTypeForm} containing the required information to update the
	 *                Content Type.
	 * @param req     The current instance of the {@link HttpServletRequest}.
	 * @param res     The current instance of the {@link HttpServletResponse}.
	 *
	 * @return The JSON response with the updated information of the Content Type.
	 */
	@PUT
	@Path("/id/{idOrVar}")
	@JSONP
	@NoCache
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces({ MediaType.APPLICATION_JSON, "application/javascript" })
	public Response updateType(@PathParam("idOrVar") final String idOrVar, final ContentTypeForm form,
							   @Context final HttpServletRequest req, @Context final HttpServletResponse res) {
		final InitDataObject initData =
				new WebResource.InitBuilder(webResource)
						.requestAndResponse(req, res)
						.requiredBackendUser(false)
						.requiredFrontendUser(false)
						.rejectWhenNoUser(true)
						.init();
		final User user = initData.getUser();
		final ContentTypeAPI contentTypeAPI = APILocator.getContentTypeAPI(user, true);
		try {
			checkNotNull(form, "The 'form' parameter is required");
			final ContentType contentType = contentTypeHelper.evaluateContentTypeRequest(
					form.getContentType(), user, false
			);
			Logger.debug(this, String.format("Updating content type: '%s'", form.getRequestJson()));
			checkNotEmpty(contentType.id(), BadRequestException.class,
					"Content Type 'id' attribute must be set");

			final Tuple2<ContentType, List<SystemActionWorkflowActionMapping>> tuple2 =
					this.saveContentTypeAndDependencies(contentType, user,
							new HashSet<>(form.getWorkflowsIds()), form.getSystemActions(),
							contentTypeAPI, false);
			final ImmutableMap.Builder<Object, Object> builderMap =
					ImmutableMap.builder()
							.putAll(contentTypeHelper.contentTypeToMap(
									contentTypeAPI.find(tuple2._1.variable()), user))
							.put(MAP_KEY_WORKFLOWS,
									this.workflowHelper.findSchemesByContentType(
											contentType.id(), initData.getUser()))
							.put(MAP_KEY_SYSTEM_ACTION_MAPPINGS, tuple2._2.stream()
									.collect(Collectors.toMap(
											SystemActionWorkflowActionMapping::getSystemAction,
											mapping -> mapping)));
			return Response.ok(new ResponseEntityView<>(builderMap.build())).build();
		} catch (final NotFoundInDbException e) {
			Logger.error(this, String.format("Content Type with ID or var name '%s' was not found", idOrVar), e);
			return ExceptionMapperUtil.createResponse(e, Response.Status.NOT_FOUND);
		} catch (final DotStateException | DotDataException e) {
			final String errorMsg = String.format("Failed to update Content Type with ID or var name " +
					"'%s': %s", idOrVar, ExceptionUtil.getErrorMessage(e));
			Logger.error(this, errorMsg, e);
			return ExceptionMapperUtil.createResponse(null, errorMsg);
		} catch (final DotSecurityException e) {
			Logger.error(this, String.format("User '%s' does not have permission to update Content Type with ID or var name " +
					"'%s'", user.getUserId(), idOrVar), e);
			throw new ForbiddenException(e);
		} catch (final Exception e) {
			Logger.error(this, String.format("An error occurred when updating Content Type with ID or var name " +
					"'%s': %s", idOrVar, ExceptionUtil.getErrorMessage(e)), e);
			return ExceptionMapperUtil.createResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Saves the specified Content Type and properly handles additional data associated to it, such
	 * as Workflow information.
	 *
	 * @param contentType          The {@link ContentType} to save.
	 * @param user                 The {@link User} executing this action.
	 * @param workflowsIds         The {@link Set} of Workflow IDs to associate to the Content
	 *                             Type.
	 * @param systemActionMappings The {@link List} of {@link Tuple2} containing the
	 *                             {@link WorkflowAPI.SystemAction} and the {@link String}
	 *                             representing the Workflow Action ID.
	 * @param contentTypeAPI       The {@link ContentTypeAPI} instance to use.
	 * @param isNew                A {@link Boolean} indicating if the Content Type is new or not.
	 *
	 * @return A {@link Tuple2} containing the saved {@link ContentType} and the {@link List} of
	 * {@link SystemActionWorkflowActionMapping} associated to it.
	 *
	 * @throws DotSecurityException The specified User doesn't have the required permissions to
	 *                              perform this action.
	 * @throws DotDataException     An error occurs when persisting the Content Type in the
	 *                              database.
	 */
	@WrapInTransaction
	private Tuple2<ContentType, List<SystemActionWorkflowActionMapping>> saveContentTypeAndDependencies (final ContentType contentType,
																								   final User user,
																								   final Set<String> workflowsIds,
																								   final List<Tuple2<WorkflowAPI.SystemAction,String>> systemActionMappings,
																								   final ContentTypeAPI contentTypeAPI,
																								   final boolean isNew) throws DotSecurityException, DotDataException {

		final List<SystemActionWorkflowActionMapping> systemActionWorkflowActionMappings = new ArrayList<>();
		final ContentType contentTypeSaved = contentTypeAPI.save(contentType);
		this.workflowHelper.saveSchemesByContentType(contentTypeSaved.id(), user, workflowsIds);

		if (!isNew) {
			this.handleFields(contentTypeSaved.id(), contentType.fieldMap(), user, contentTypeAPI);
		}

		if (UtilMethods.isSet(systemActionMappings)) {

			for (final Tuple2<WorkflowAPI.SystemAction,String> tuple2 : systemActionMappings) {

				final WorkflowAPI.SystemAction systemAction = tuple2._1;
				final String workflowActionId               = tuple2._2;
				if (UtilMethods.isSet(workflowActionId)) {

					Logger.warn(this, "Saving the system action: " + systemAction +
							", for content type: " + contentTypeSaved.variable() + ", with the workflow action: "
							+ workflowActionId );

					systemActionWorkflowActionMappings.add(this.workflowHelper.mapSystemActionToWorkflowAction(new WorkflowSystemActionForm.Builder()
							.systemAction(systemAction).actionId(workflowActionId)
							.contentTypeVariable(contentTypeSaved.variable()).build(), user));
				} else if (UtilMethods.isSet(systemAction)) {

					if (!isNew) {
						Logger.warn(this, "Deleting the system action: " + systemAction +
								", for content type: " + contentTypeSaved.variable());

						final SystemActionWorkflowActionMapping mappingDeleted =
								this.workflowHelper.deleteSystemAction(systemAction, contentTypeSaved, user);

						Logger.warn(this, "Deleted the system action mapping: " + mappingDeleted);
					}
				} else {

					throw new IllegalArgumentException("On System Action Mappings, a system action has been sent null or empty");
				}
			}
		}

		return Tuple.of(contentTypeSaved, systemActionWorkflowActionMappings);
	}

	/**
	 * We need to handle in this way b/c when the content type exists the fields are not being
	 * updated
	 *
	 * @param contentTypeId           the content type id
	 * @param newContentTypeFieldsMap the content type fields found in the request
	 * @param user                    the user performing the action
	 * @param contentTypeAPI          the content type api
	 * @throws DotDataException     if there is an error with the data
	 * @throws DotSecurityException if the user does not have the required permissions
	 */
	@WrapInTransaction
	private void handleFields(final String contentTypeId,
			final Map<String, Field> newContentTypeFieldsMap, final User user,
			final ContentTypeAPI contentTypeAPI) throws DotDataException, DotSecurityException {

		final ContentType currentContentType = contentTypeAPI.find(contentTypeId);

		final DiffResult<FieldDiffItemsKey, Field> diffResult = new FieldDiffCommand(contentTypeId)
				.applyDiff(currentContentType.fieldMap(), newContentTypeFieldsMap);

		if (!diffResult.getToDelete().isEmpty()) {
			APILocator.getContentTypeFieldLayoutAPI().deleteField(
					currentContentType,
					diffResult.getToDelete().values().stream().
							map(Field::id).
							collect(Collectors.toList()),
					user);
		}

		if (!diffResult.getToAdd().isEmpty()) {
			APILocator.getContentTypeFieldAPI().saveFields(
					new ArrayList<>(diffResult.getToAdd().values()), user
			);
		}

		if (!diffResult.getToUpdate().isEmpty()) {
			handleUpdateFieldAndFieldVariables(user, diffResult);
		}
	}

	/**
	 * Handles the update of fields and field variables based on the difference result.
	 *
	 * @param user          The user performing the update.
	 * @param diffResult    The result of the field differences.
	 * @throws DotSecurityException If a security exception occurs.
	 * @throws DotDataException     If a data exception occurs.
	 */
	private void handleUpdateFieldAndFieldVariables(
			final User user, final DiffResult<FieldDiffItemsKey, Field> diffResult)
			throws DotSecurityException, DotDataException {

		final List<Field> fieldToUpdate = new ArrayList<>();
		final List<Tuple2<Field, List<DiffItem>>> fieldVariableToUpdate = new ArrayList<>();

		for (final Map.Entry<FieldDiffItemsKey, Field> entry : diffResult.getToUpdate().entrySet()) {

			final Map<Boolean, List<DiffItem>> diffPartition = // split the differences between the ones that are for the field and the ones that are for field variables
					entry.getKey().getDiffItems().stream().collect(Collectors.partitioningBy(diff -> diff.getVariable().startsWith("fieldVariable.")));
			final List<DiffItem> fieldVariableList = diffPartition.get(Boolean.TRUE);  // field variable diffs
			final List<DiffItem> fieldList         = diffPartition.get(Boolean.FALSE); // field diffs
			if (UtilMethods.isSet(fieldList)) {
				Logger.debug(this, "Updating the field : " + entry.getValue().variable() + " diff: "
						+ fieldList);
				fieldToUpdate.add(entry.getValue());
			}

			if (UtilMethods.isSet(fieldVariableList)) {
				Logger.debug(this, "Updating the field - field Variables : " + entry.getValue().variable() + " diff: " + fieldVariableList);
				fieldVariableToUpdate.add(Tuple.of(entry.getValue(), fieldVariableList));
			}
		}

		if (UtilMethods.isSet(fieldToUpdate)) { // any diff on fields, so update the fields (but not update field variables :( )
			APILocator.getContentTypeFieldAPI().saveFields(fieldToUpdate, user);
		}

		// any diff on field variables, lets see what kind of diffs are.
		if (UtilMethods.isSet(fieldVariableToUpdate)) {
			handleUpdateFieldVariables(user, fieldVariableToUpdate);
		}
	}

	/**
	 * Handles the update of field variables for a given user and a list of field variable tuples.
	 *
	 * @param user                  The user object for which the field variables will be updated.
	 * @param fieldVariableToUpdate List of tuples containing the field and a list of diff items to
	 *                              update.
	 * @throws DotDataException     If there is an error accessing the data.
	 * @throws DotSecurityException If there is a security error.
	 */
	private void handleUpdateFieldVariables(
			final User user, final List<Tuple2<Field, List<DiffItem>>> fieldVariableToUpdate)
			throws DotDataException, DotSecurityException {

		for (final Tuple2<Field, List<DiffItem>> fieldVariableTuple : fieldVariableToUpdate) {
			handleUpdateFieldVariables(user, fieldVariableTuple);
		}
	}

	/**
	 * Handles the update of field variables for a user and field variable tuple.
	 *
	 * @param user               the user performing the update
	 * @param fieldVariableTuple the tuple containing the field and list of diff items
	 * @throws DotDataException     if there is an issue with data access
	 * @throws DotSecurityException if there is a security issue
	 */
	private void handleUpdateFieldVariables(
			final User user, final Tuple2<Field, List<DiffItem>> fieldVariableTuple)
			throws DotDataException, DotSecurityException {

		final Map<String, FieldVariable> fieldVariableMap =
				fieldVariableTuple._1().fieldVariablesMap();
		for (final DiffItem diffItem : fieldVariableTuple._2()) {

			final var detail = diffItem.getDetail();

			// normalizing the real varname
			final String fieldVariableVarName = StringUtils.replace(diffItem.getVariable(),
					"fieldVariable.", StringPool.BLANK);
			if ("delete".equals(detail) &&
					fieldVariableMap.containsKey(fieldVariableVarName)) {

				APILocator.getContentTypeFieldAPI()
						.delete(fieldVariableMap.get(fieldVariableVarName));
			}

			// if add or update, it is pretty much the same
			if ("add".equals(detail) || "update".equals(detail)) {

				if ("update".equals(detail) &&
						!fieldVariableMap.containsKey(fieldVariableVarName)) {
					// on update get the current field and gets the id
					continue;
				}

				APILocator.getContentTypeFieldAPI()
						.save(fieldVariableMap.get(fieldVariableVarName), user);
			}
		}
	}

	@DELETE
	@Path("/id/{idOrVar}")
	@JSONP
	@NoCache
	@Produces({MediaType.APPLICATION_JSON, "application/javascript"})
	public Response deleteType(@PathParam("idOrVar") final String idOrVar, @Context final HttpServletRequest req, @Context final HttpServletResponse res)
			throws JSONException {

		final InitDataObject initData = this.webResource.init(null, req, res, true, null);
		final User user = initData.getUser();

		final ContentTypeAPI contentTypeAPI = APILocator.getContentTypeAPI(user, true);

		try {
			ContentType type;
			try {
				type = contentTypeAPI.find(idOrVar);
			} catch (NotFoundInDbException nfdb) {
				return Response.status(404).build();
			}

			contentTypeAPI.delete(type);

			JSONObject joe = new JSONObject();
			joe.put("deleted", type.id());

			return Response.ok(new ResponseEntityView<>(joe.toString())).build();
		} catch (final DotSecurityException e) {
			throw new ForbiddenException(e);
		} catch (final Exception e) {
			Logger.error(this, String.format("Error deleting content type identified by (%s) ",idOrVar), e);
			return ExceptionMapperUtil.createResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	@GET
	@Path("/id/{idOrVar}")
	@JSONP
	@NoCache
	@Produces({MediaType.APPLICATION_JSON, "application/javascript"})
	public Response getType(
			@PathParam("idOrVar") final String idOrVar,
			@Context final HttpServletRequest req,
			@Context final HttpServletResponse res,
			@QueryParam("languageId") final Long languageId,
			@QueryParam("live") final Boolean paramLive)
			throws DotDataException {

		final InitDataObject initData = this.webResource.init(null, req, res, false, null);
		final User user = initData.getUser();
		ContentTypeAPI tapi = APILocator.getContentTypeAPI(user, true);
		Response response = Response.status(404).build();
        final HttpSession session = req.getSession(false);
		try {

			Logger.debug(this, ()-> "Getting the Type: " + idOrVar);

			final ContentType type = tapi.find(idOrVar);
			if (null == type) {
				// Humoring sonarlint, this block should never be reached as the find method will
				// throw an exception if the type is not found.
				throw new NotFoundInDbException(
						String.format("Content Type with ID or var name '%s' was not found", idOrVar
						));
			}

			if (null != session) {
				session.setAttribute(SELECTED_STRUCTURE_KEY, type.inode());
			}

			final boolean live = paramLive == null ?
					(PageMode.get(Try.of(HttpServletRequestThreadLocal.INSTANCE::getRequest).getOrNull())).showLive
					: paramLive;

			final ContentTypeInternationalization contentTypeInternationalization = languageId != null ?
					new ContentTypeInternationalization(languageId, live, user) : null;
			final ImmutableMap<Object, Object> resultMap = ImmutableMap.builder()
					.putAll(contentTypeHelper.contentTypeToMap(type,
							contentTypeInternationalization, user))
					.put(MAP_KEY_WORKFLOWS, this.workflowHelper.findSchemesByContentType(
							type.id(), initData.getUser()))
					.put(MAP_KEY_SYSTEM_ACTION_MAPPINGS,
							this.workflowHelper.findSystemActionsByContentType(
									type, initData.getUser()).stream()
							.collect(Collectors.toMap(mapping -> mapping.getSystemAction(),
									mapping -> mapping))).build();

			response = ("true".equalsIgnoreCase(req.getParameter("include_permissions")))?
					Response.ok(new ResponseEntityView<>(resultMap, PermissionsUtil.getInstance().getPermissionsArray(type, initData.getUser()))).build():
					Response.ok(new ResponseEntityView<>(resultMap)).build();
		} catch (final DotSecurityException e) {
			throw new ForbiddenException(e);
		} catch (final NotFoundInDbException nfdb2) {
			// nothing to do here, will throw a 404
		}

		return response;
	}

	/**
	 * Returns the list of Content Type objects that match the specified filter and the optional pagination criteria.
	 * <p>Example:</p>
	 * <pre>
	 * {@code
	 *     {{serverURL}}/api/v1/contenttype/_filter
	 * }
	 * </pre>
	 * JSON body:
	 * <pre>
	 * {@code
	 *     {
	 *         "filter" : {
	 *             "data" : "calendarEvent,Vanityurl,webPageContent,DotAsset,persona",
	 *             "query": ""
	 *         },
	 *         "page": 0,
	 *         "perPage": 5
	 *     }
	 * }
	 * </pre>
	 *
	 * @param req  The current {@link HttpServletRequest} instance.
	 * @param res  The current {@link HttpServletResponse} instance.
	 * @param form The {@link FilteredContentTypesForm} containing the required information and optional pagination
	 *             parameters.
	 *
	 * @return The JSON response with the Content Types matching the specified Velocity Variable Names.
	 */
	@POST
	@Path("/_filter")
	@JSONP
	@NoCache
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces({MediaType.APPLICATION_JSON, "application/javascript"})
	public final Response filteredContentTypes(@Context final HttpServletRequest req,
											   @Context final HttpServletResponse res,
											   final FilteredContentTypesForm form) {
		if (null == form) {
			return ExceptionMapperUtil.createResponse(null, "Requests to '_filter' need a POST JSON body");
		}
		final InitDataObject initData = this.webResource.init(null, req, res, true, null);
		final User user = initData.getUser();
		Response response;
		final String types = getFilterValue(form, "types", StringPool.BLANK);
		final List<String> typeVarNames = UtilMethods.isSet(types) ? Arrays.asList(types.split(COMMA)) : null;
		final String filter = getFilterValue(form, "query", StringPool.BLANK);
		final Map<String, Object> extraParams = new HashMap<>();
		if (UtilMethods.isSet(typeVarNames)) {
			extraParams.put(ContentTypesPaginator.TYPES_PARAMETER_NAME, typeVarNames);
		}
		try {
			final PaginationUtil paginationUtil =
					new PaginationUtil(new ContentTypesPaginator(APILocator.getContentTypeAPI(user)));
			response = paginationUtil.getPage(req, user, filter, form.getPage(), form.getPerPage(), form.getOrderBy(),
					OrderDirection.valueOf(form.getDirection()), extraParams);
		} catch (final Exception e) {
			if (ExceptionUtil.causedBy(e, DotSecurityException.class)) {
				throw new ForbiddenException(e);
			}
			response = ExceptionMapperUtil.createResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
			Logger.error(this, e.getMessage(), e);
		}
		return response;
	}

	@GET
	@Path("/basetypes")
	@JSONP
	@InitRequestRequired
	@NoCache
	@Produces({MediaType.APPLICATION_JSON, "application/javascript"})
	public final Response getRecentBaseTypes(@Context final HttpServletRequest request) {
		Response response;
		try {
			final List<BaseContentTypesView> types = contentTypeHelper.getTypes(request);
			response = Response.ok(new ResponseEntityView<>(types)).build();
		} catch (Exception e) { // this is an unknown error, so we report as a 500.

			response = ExceptionMapperUtil.createResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
		}

		return response;
	} // getTypes.

	/**
	 * Returns a list of {@link ContentType} objects based on the filtering criteria. This is how
	 * you can call this endpoint:
	 * <pre>{@code
	 * GET http://localhost:8080/api/v1/contenttype?sites=48190c8c-42c4-46af-8d1a-0cd5db894797,SYSTEM_HOST,&per_page=40&&orderby=variabledirection=DESC
	 * }</pre>
	 * <p>If you want results composed of 10 items per page and you want the third page, and you
	 * don't have the Site's Identifier, you can call this URL:</p>
	 * <pre>{@code
	 * GET http://localhost:8080/api/v1/contenttype?sites=demo.dotcms.com&page=3&per_page=10
	 * }</pre>
	 *
	 * @param httpRequest  The current instance of the {@link HttpServletRequest}.
	 * @param httpResponse The current instance of the {@link HttpServletResponse}.
	 * @param filter       Filtering parameter used to pass down the Content Types name, Velocity
	 *                     Variable Name, or Inode. You can pass down part of the characters.
	 * @param page         The selected results page, for pagination purposes.
	 * @param perPage      The number of results to return per page, for pagination purposes.
	 * @param orderByParam The column name that will be used to sort the paginated results. For
	 *                     reference, please check
	 *                     {@link com.dotmarketing.common.util.SQLUtil#ORDERBY_WHITELIST}.
	 * @param direction    The direction of the sorting. It can be either "ASC" or "DESC".
	 * @param type         The Velocity variable name of the Content Type  to retrieve.
	 * @param siteId       The identifier of the Site where the requested Content Types live.
	 * @param sites        A comma-separated list of Site identifiers or Site Keys where the
	 *                     requested Content Types live.
	 *
	 * @return A JSON response with the paginated list of Content Types.
	 *
	 * @throws DotDataException An error occurred when retrieving information from the database.
	 */
	@GET
	@JSONP
	@NoCache
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces({MediaType.APPLICATION_JSON, "application/javascript"})
	public final Response getContentTypes(@Context final HttpServletRequest httpRequest,
										  @Context final HttpServletResponse httpResponse,
										  @QueryParam(PaginationUtil.FILTER) final String filter,
										  @QueryParam(PaginationUtil.PAGE) final int page,
										  @QueryParam(PaginationUtil.PER_PAGE) final int perPage,
										  @DefaultValue("upper(name)") @QueryParam(PaginationUtil.ORDER_BY) String orderByParam,
										  @DefaultValue("ASC") @QueryParam(PaginationUtil.DIRECTION) String direction,
										  @QueryParam("type") String type,
										  @QueryParam(ContentTypesPaginator.HOST_PARAMETER_ID) final String siteId,
										  @QueryParam(ContentTypesPaginator.SITES_PARAMETER_NAME) final String sites) throws DotDataException {

		final User user = new WebResource.InitBuilder(this.webResource)
				.requestAndResponse(httpRequest, httpResponse)
				.rejectWhenNoUser(true)
				.init().getUser();
		final String orderBy = this.getOrderByRealName(orderByParam);
		try {
			final Map<String, Object> extraParams = new HashMap<>();
			if (null != type) {
				extraParams.put(ContentTypesPaginator.TYPE_PARAMETER_NAME, type);
			}
			if (null != siteId) {
				extraParams.put(ContentTypesPaginator.HOST_PARAMETER_ID,siteId);
			}
			if (UtilMethods.isSet(sites)) {
				extraParams.put(ContentTypesPaginator.SITES_PARAMETER_NAME,
						Arrays.asList(sites.split(COMMA)));
			}
			final PaginationUtil paginationUtil = new PaginationUtil(new ContentTypesPaginator(APILocator.getContentTypeAPI(user)));
			return paginationUtil.getPage(httpRequest, user, filter, page, perPage, orderBy,
					OrderDirection.valueOf(direction), extraParams);
		} catch (final IllegalArgumentException e) {
			throw new DotDataException(String.format("An error occurred when listing Content Types: " +
					"%s", ExceptionUtil.getErrorMessage(e)));
		} catch (final Exception e) {
			if (ExceptionUtil.causedBy(e, DotSecurityException.class)) {
				throw new ForbiddenException(e);
			}
			Logger.error(this, String.format("An error occurred when listing Content Types: " +
					"%s", ExceptionUtil.getErrorMessage(e)), e);
			return ExceptionMapperUtil.createResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	private String getOrderByRealName(final String orderbyParam) {
		if ("modDate".equals(orderbyParam)){
			return "mod_date";
		}else if ("variable".equals(orderbyParam)) {
			return "velocity_var_name";
		} else {
			return orderbyParam;
		}
	}

	/**
	 * Utility method used to return a specific parameter from the Content Type Filtering Form. If not present, a
	 * default value will be returned.
	 *
	 * @param form         The {@link FilteredContentTypesForm} in the request.
	 * @param param        The parameter being requested.
	 * @param defaultValue The default value in case the parameter is not present or set.
	 *
	 * @return The form parameter or the specified default value.
	 */
	@SuppressWarnings("unchecked")
	private <T> T getFilterValue(final FilteredContentTypesForm form, final String param, T defaultValue) {
		if (null == form || null == form.getFilter() || form.getFilter().isEmpty()) {
			return defaultValue;
		}
		return UtilMethods.isSet(form.getFilter().get(param)) ? (T) form.getFilter().get(param) : defaultValue;
	}

}
