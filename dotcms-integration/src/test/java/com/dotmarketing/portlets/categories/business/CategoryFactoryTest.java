package com.dotmarketing.portlets.categories.business;

import static com.dotcms.util.CollectionsUtils.list;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.dotcms.IntegrationTestBase;
import com.dotcms.api.vtl.model.DotJSON;
import com.dotcms.cache.DotJSONCacheAddTestCase;
import com.dotcms.datagen.CategoryDataGen;
import com.dotcms.util.IntegrationTestInitService;
import com.dotcms.util.pagination.OrderDirection;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.FactoryLocator;
import com.dotmarketing.common.db.DotConnect;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.portlets.categories.model.Category;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.liferay.util.StringUtil;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import net.bytebuddy.utility.RandomString;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/***
 * Category Factory Test
 */
@RunWith(DataProviderRunner.class)
public class CategoryFactoryTest extends IntegrationTestBase {

    private static CategoryFactory categoryFactory;

    @BeforeClass
    public static void prepare() throws Exception {
        IntegrationTestInitService.getInstance().init();
        categoryFactory = FactoryLocator.getCategoryFactory();
    }

    /**
     * Method to test: {@link CategoryFactory#save(Category)}
     * Given Scenario: attempt to save an empty category object
     * ExpectedResult: Should throw a DotDataException
     * @throws DotDataException
     */
    @Test(expected = DotDataException.class)
    public void Test_Save_Empty_Category_Expect_Exception() throws DotDataException {
        Category category = new Category();
        categoryFactory.save(category);
    }

    /**
     * Method to test: {@link CategoryFactory#save(Category)}
     * Given Scenario: attempt to save an empty category object
     * ExpectedResult: We should be able to find the Category created using an existing inode
     * @throws DotDataException
     */
    @Test
    public void Test_Update_Non_Existing_Category_Expect_Exception() throws DotDataException {
        Category category = newCategory();
        category.setInode("lol-"+System.currentTimeMillis());
        categoryFactory.save(category);
        Category savedCategory = categoryFactory.find(category.getInode());
        categoryFactory.delete(savedCategory);
        assertNull(categoryFactory.find(category.getInode()));
    }

    /**
     * Method to test: {@link CategoryFactory#find(String)}
     * Given Scenario: attempt to find a non existing category
     * ExpectedResult: Should return null
     * @throws DotDataException
     */
    @Test
    public void Test_Find_Non_Existing_Category_By_Id() throws DotDataException {
        Category category = categoryFactory.find("lol-"+System.currentTimeMillis());
        assertNull(category);
    }

    /**
     * Method to test: {@link CategoryFactory#findByKey(String)}
     * Given Scenario: attempt to find a non existing category
     * ExpectedResult: Should return null
     * @throws DotDataException
     */
    @Test
    public void Test_Find_Non_Existing_Category_By_Key() throws DotDataException {
        final Category category = categoryFactory.findByKey("lol-"+System.currentTimeMillis());
        assertNull(category);
    }

    @NotNull
    private Category newCategory() throws DotDataException {
        String categoryName = "cat_" + System.currentTimeMillis();
        final Category category = new Category();
        category.setCategoryName(categoryName);
        category.setCategoryVelocityVarName(categoryName);
        category.setSortOrder(1);
        category.setKey("key_"+System.currentTimeMillis());
        category.setDescription("");
        category.setActive(true);
        category.setKeywords("k1,k2,k3");
        categoryFactory.save(category);
        return category;
    }

    /**
     * Method to test: {@link CategoryFactory#save(Category)}
     * Given Scenario: attempt to save a category object then find it in different ways
     * ExpectedResult: Should be found everytime
     * @throws DotDataException
     */
    @Test
    public void Test_Save_Brand_New_Category_Then_Find_It() throws DotDataException {
        final Category category = newCategory();
        assertNotNull(category);
        assertNotNull(category.getInode());

        assertNotNull(categoryFactory.find(category.getInode()));
        assertNotNull(categoryFactory.findByKey(category.getKey()));
        assertNotNull(categoryFactory.findByName(category.getCategoryName()));
        assertNotNull(categoryFactory.findByVar(category.getCategoryVelocityVarName()));
    }

    /**
     * Method to test: {@link CategoryFactory#findAll()}
     * Given Scenario: get all categories then remove them then use find all again
     * ExpectedResult: After having removed all categories whe should expect none
     * @throws DotDataException
     */
    @Test
    public void Test_Find_All_Then_Delete_All() throws DotDataException {
        final Category category = newCategory();
        assertNotNull(category);
        final List<Category> all = categoryFactory.findAll();
        assertFalse(all.isEmpty());

        for(final Category cat:all){
          categoryFactory.delete(cat);
        }

        final List<Category> allAfterDelete = categoryFactory.findAll();
        assertTrue(allAfterDelete.isEmpty());
    }

    /**
     * Method to test: {@link CategoryFactory#getChildren(Categorizable)} {@link CategoryFactory#findChildrenByFilter(String, String, String)}
     * Given Scenario: create a parent-child category hierarchy the test the  child finders
     * ExpectedResult: Once removed the finders should return empty and vice-versa
     * @throws DotDataException
     */
    @Test
    public void Test_Find_Children_Then_Remove_Em() throws DotDataException {
        final Category parent = newCategory();
        assertNotNull(parent);
        final Category child = newCategory();
        assertNotNull(child);
        categoryFactory.addChild(parent, child, null);
        assertFalse(categoryFactory.getChildren(parent).isEmpty());
        assertFalse(categoryFactory.findChildrenByFilter(parent.getInode(), child.getCategoryName(),null).isEmpty());
        categoryFactory.removeChildren(parent);
        assertTrue(categoryFactory.getChildren(parent).isEmpty());
        assertTrue(categoryFactory.findChildrenByFilter(parent.getInode(), child.getCategoryName(),null).isEmpty());
    }

    /**
     * Method to test: {@link CategoryFactory#getParents(Categorizable, String)}
     * Given Scenario: create a parent-child category hierarchy the test the  child finders
     * ExpectedResult: Once removed the finders should return empty and vice-versa
     * @throws DotDataException
     */
    @Test
    public void Test_Find_Parent_Then_Remove_Em() throws DotDataException {
        final Category parent = newCategory();
        assertNotNull(parent);
        final Category child = newCategory();
        assertNotNull(child);
        categoryFactory.addChild(parent, child, null);
        assertFalse(categoryFactory.getParents(child).isEmpty());
        categoryFactory.delete(parent);
        assertTrue(categoryFactory.getParents(child).isEmpty());
    }

    /**
     * Method to test: {@link CategoryFactory#findTopLevelCategories()}
     * Given Scenario: create parent children items call top level finder
     * ExpectedResult: verify top level is found then delete parent call finder again. expect empty collection
     * @throws DotDataException
     */
    @Test
    public void Test_Find_Top_Level_Categories() throws DotDataException {
        final Category parent = newCategory();
        final Category child = newCategory();
        categoryFactory.addChild(parent, child, null);
        assertTrue(categoryFactory.findTopLevelCategories().stream().anyMatch(category -> parent.getInode().equals(category.getInode())));
        assertTrue(categoryFactory.findTopLevelCategories().stream().noneMatch(category -> child.getInode().equals(category.getInode())));
    }

    /**
     * Method to test: {@link CategoryFactory#sortChildren(String)}
     * Given Scenario: create parent children items alter order, save then find
     * ExpectedResult: items should be in natural ascending order
     * @throws DotDataException
     */
    @Test
    public void Test_ReOrder_Categories() throws DotDataException {
        final Category parent = newCategory();
        assertNotNull(parent);
        final Category child1 = newCategory();
        final Category child2 = newCategory();
        final Category child3 = newCategory();

        child1.setSortOrder(3);
        categoryFactory.save(child1);
        child2.setSortOrder(2);
        categoryFactory.save(child2);
        child3.setSortOrder(1);
        categoryFactory.save(child3);

        categoryFactory.sortChildren(parent.getInode());

        final List<Category> children = categoryFactory.getChildren(parent);
        int orderCount = 1;
        for(Category category:children){
            assertEquals(orderCount,category.getSortOrder().intValue());
            switch (orderCount){
                case 1:{
                    assertEquals(child3.getInode(),category.getInode());
                    break;
                }
                case 2:{
                    assertEquals(child2.getInode(),category.getInode());
                    break;
                }
                case 3:{
                    assertEquals(child1.getInode(),category.getInode());
                    break;
                }
            }
            orderCount++;
        }
    }

    /**
     * Method to test: {@link CategoryFactory#getParents(Categorizable, String)}
     * @throws DotDataException
     */ 
    @Test
    public void Test_Get_Parent_Categories() throws DotDataException {

        final Category root = newCategory();
        final Category root2 = newCategory();
        final Category leaf1 = newCategory();
        final Category leaf2 = newCategory();
        final Category leaf3 = newCategory();

        categoryFactory.save(root);

        categoryFactory.addChild(root, leaf1, null);
        categoryFactory.addChild(root2, leaf1, null);

        List<Category> parents = categoryFactory.getParents(leaf1);
        assertEquals(2,parents.size());

        categoryFactory.addChild(leaf1, leaf2, null);
        categoryFactory.addChild(leaf2, leaf3, null);

        parents = categoryFactory.getParents(leaf3);
        assertEquals(1,parents.size());

    }

    /**
     * Method to test: {@link CategoryFactory#hasDependencies(Category)}
     * Given Scenario: create parent test if has dependencies then add a child and test again
     * ExpectedResult: initially the root should not show any dependencies until we add a child
     * @throws DotDataException
     */
    @Test
    public void Test_Has_Dependencies() throws DotDataException {
        final Category root = newCategory();
        assertFalse(categoryFactory.hasDependencies(root));
        final Category leaf1 = newCategory();
        categoryFactory.addChild(root, leaf1, null);
        assertTrue(categoryFactory.hasDependencies(root));
    }

    @DataProvider
    public static Object[] findCategoriesFilters() {
        final String stringToFilterBy = new RandomString().nextString();

        return new FilterTestCase[] {
                new FilterTestCase(stringToFilterBy, String::toLowerCase),
                new FilterTestCase(stringToFilterBy, String::toUpperCase),
                new FilterTestCase(stringToFilterBy, (filter) ->
                        filter.substring(1, filter.length()/2).toLowerCase() +
                                filter.substring(filter.length()/2).toUpperCase())
        };
    }

    /**
     * Method to test: {@link CategoryFactoryImpl#findAll(CategoryFactory.CategorySearchCriteria)}
     * When:
     *
     * - Create a random string to be used as the filter for the test.
     * - Create two top-level categories, named topLevelCategory_1 and topLevelCategory_2, and include the filter in their names.
     * - For topLevelCategory_1, create four children:
     *     Include the filter in three of these children: one in the key, one in the name, and one in the variable name.
     *     The fourth child should not include the filter anywhere.
     * - Add a child to the last child of topLevelCategory_1 (the one without the filter) and include the filter in its name.
     * Also, create a grandchild and include the filter in its name.
     * - Create another child for topLevelCategory_1 and include the filter in the key, name, and variable name.
     * - Call the method with the filter
     *
     * Should:
     *
     * Return five categories: the three children of topLevelCategory_1 that include the filter and the two grandchildren.
     */
    @Test
    @UseDataProvider("findCategoriesFilters")
    public void getAllCategoriesFiltered(final FilterTestCase filterTestCase) throws DotDataException {

        final String stringToFilterBy = filterTestCase.filter;

        final Category topLevelCategory_1 = new CategoryDataGen().setCategoryName("Top Level Category " + filterTestCase.filter)
                .setKey("top_level_categoria")
                .setCategoryVelocityVarName("top_level_categoria")
                .nextPersisted();

        final Category childCategory_1 = new CategoryDataGen().setCategoryName("Child Category 1")
                .setKey("child_category_1 " + filterTestCase.filter)
                .setCategoryVelocityVarName("child_category_1")
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category childCategory_2 = new CategoryDataGen().setCategoryName("Child Category 2")
                .setKey("child_category_2")
                .setCategoryVelocityVarName("child_category_2 " + filterTestCase.filter)
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category childCategory_3 = new CategoryDataGen().setCategoryName("Child Category 3 "  + filterTestCase.filter)
                .setKey("child_category_3")
                .setCategoryVelocityVarName("child_category_3")
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category childCategory_4 = new CategoryDataGen().setCategoryName("Child Category 4")
                .setKey("child_category_4")
                .setCategoryVelocityVarName("child_category_4")
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category childCategory_6 = new CategoryDataGen().setCategoryName(filterTestCase.filter + "Child Category 6")
                .setKey("child_category_6")
                .setCategoryVelocityVarName("child_category_6")
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category childCategory_7 = new CategoryDataGen().setCategoryName("Child " + filterTestCase.filter + "Category 7")
                .setKey("child_category_7")
                .setCategoryVelocityVarName("child_category_7")
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category grandchildCategory_1 = new CategoryDataGen().setCategoryName("Grand Child Category 1 " + filterTestCase.filter)
                .setKey("grand_child_category_1")
                .setCategoryVelocityVarName("grand_child_category_1")
                .parent(childCategory_4)
                .nextPersisted();

        final Category grandchildCategory_2 = new CategoryDataGen().setCategoryName("Grand Child Category 2 " + filterTestCase.filter)
                .setKey("grand_child_category_2")
                .setCategoryVelocityVarName("grand_child_category_2")
                .parent(grandchildCategory_1)
                .nextPersisted();

        final Category topLevelCategory_2 = new CategoryDataGen().setCategoryName("Top Level Category "  + filterTestCase.filter)
                .setKey("top_level_category_2")
                .setCategoryVelocityVarName("top_level_category_2")
                .nextPersisted();

        final Category childCategory_5 = new CategoryDataGen().setCategoryName("Child Category 5"  + filterTestCase.filter)
                .setKey("child_category_5 " + filterTestCase.filter)
                .setCategoryVelocityVarName("child_category_5 " +  filterTestCase.filter)
                .parent(topLevelCategory_2)
                .nextPersisted();


        List<String> categoriesExpected = list(childCategory_1, childCategory_2, childCategory_3, grandchildCategory_1,
                grandchildCategory_2, childCategory_6, childCategory_7).stream().map(Category::getInode).collect(Collectors.toList());

        final CategoryFactory.CategorySearchCriteria categorySearchCriteria = new CategoryFactory.CategorySearchCriteria.Builder()
                .filter(filterTestCase.transformToSearch())
                .rootInode(topLevelCategory_1.getInode())
                .build();

        final List<String> categories = FactoryLocator.getCategoryFactory().findAll(categorySearchCriteria)
            .stream().map(Category::getInode).collect(Collectors.toList());
        assertEquals(categoriesExpected.size(), categories.size());
        assertTrue(categories.containsAll(categoriesExpected));
    }

    /**
     * Method to test: {@link CategoryFactoryImpl#findAll(CategoryFactory.CategorySearchCriteria)}
     * When: call the method with filter and inode equals to null
     * Should: return all the Categories
     *
     * @throws DotDataException
     */
    @Test
    public void getAllCategoriesWithNullFilterAndInode() throws DotDataException {
        final Category topLevelCategory_1 = new CategoryDataGen().setCategoryName("Top Level Category")
                .setKey("top_level_categoria")
                .setCategoryVelocityVarName("top_level_categoria")
                .nextPersisted();

        final Category childCategory_1 = new CategoryDataGen().setCategoryName("Child Category 1")
                .setKey("child_category_1")
                .setCategoryVelocityVarName("child_category_1")
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category childCategory_2 = new CategoryDataGen().setCategoryName("Child Category 2")
                .setKey("child_category_2")
                .setCategoryVelocityVarName("child_category_2")
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category grandchildCategory_1 = new CategoryDataGen().setCategoryName("Grand Child Category 1")
                .setKey("grand_child_category_1")
                .setCategoryVelocityVarName("grand_child_category_1")
                .parent(childCategory_2)
                .nextPersisted();

        final CategoryFactory.CategorySearchCriteria categorySearchCriteria = new CategoryFactory.CategorySearchCriteria.Builder()
                .build();

        final Collection<Category> categoriesWithFilter = FactoryLocator.getCategoryFactory().findAll(categorySearchCriteria);
        final List<Category> categoriesWithoutFilter = FactoryLocator.getCategoryFactory().findAll();

        assertTrue(Objects.deepEquals(categoriesWithoutFilter, categoriesWithFilter));
    }

    /**
     * Method to test: {@link CategoryFactoryImpl#findAll(CategoryFactory.CategorySearchCriteria)}
     * When: call the method with filter  equals to null and inode not null
     * Should: return all children  Categories
     *
     * @throws DotDataException
     */
    @Test
    public void getAllCategoriesWithNullFilter() throws DotDataException {
        final Category topLevelCategory_1 = new CategoryDataGen().setCategoryName("Top Level Category 1")
                .setKey("top_level_categoria_1")
                .setCategoryVelocityVarName("top_level_categoria_1")
                .nextPersisted();

        final Category childCategory_1 = new CategoryDataGen().setCategoryName("Child Category 1")
                .setKey("child_category_1")
                .setCategoryVelocityVarName("child_category_1")
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category childCategory_2 = new CategoryDataGen().setCategoryName("Child Category 2")
                .setKey("child_category_2")
                .setCategoryVelocityVarName("child_category_2")
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category childCategory_3 = new CategoryDataGen().setCategoryName("Child Category 3 ")
                .setKey("child_category_3")
                .setCategoryVelocityVarName("child_category_3")
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category childCategory_4 = new CategoryDataGen().setCategoryName("Child Category 4")
                .setKey("child_category_4")
                .setCategoryVelocityVarName("child_category_4")
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category grandchildCategory_1 = new CategoryDataGen().setCategoryName("Grand Child Category 1")
                .setKey("grand_child_category_1")
                .setCategoryVelocityVarName("grand_child_category_1")
                .parent(childCategory_4)
                .nextPersisted();

        final Category grandchildCategory_2 = new CategoryDataGen().setCategoryName("Grand Child Category 2")
                .setKey("grand_child_category_2")
                .setCategoryVelocityVarName("grand_child_category_2")
                .parent(grandchildCategory_1)
                .nextPersisted();

        final Category topLevelCategory_2 = new CategoryDataGen().setCategoryName("Top Level Category 2")
                .setKey("top_level_category_2")
                .setCategoryVelocityVarName("top_level_category_2")
                .nextPersisted();

        final Category childCategory_5 = new CategoryDataGen().setCategoryName("Child Category 5")
                .setKey("child_category_5")
                .setCategoryVelocityVarName("child_category_5")
                .parent(topLevelCategory_2)
                .nextPersisted();

        final CategoryFactory.CategorySearchCriteria categorySearchCriteria = new CategoryFactory.CategorySearchCriteria.Builder()
                .rootInode(topLevelCategory_1.getInode())
                .build();

        List<String> categoriesExpected = list(childCategory_1, childCategory_2, childCategory_3, childCategory_4,
                grandchildCategory_1, grandchildCategory_2).stream().map(Category::getInode).collect(Collectors.toList());

        final List<String> categories = FactoryLocator.getCategoryFactory().findAll(categorySearchCriteria)
                .stream().map(Category::getInode).collect(Collectors.toList());
        assertEquals(categoriesExpected.size(), categories.size());
        assertTrue(categories.containsAll(categoriesExpected));
    }

    /**
     * Method to test: {@link CategoryFactoryImpl#findAll(CategoryFactory.CategorySearchCriteria)}
     * When: Create a set of {@link Category} and called the method ordering by key
     * Should: return all children  Categories ordered
     *
     * @throws DotDataException
     */
    @Test
    public void getAllCategoriesFilteredOrdered() throws DotDataException {
        final Category topLevelCategory_1 = new CategoryDataGen().setCategoryName("Top Level Category 1")
                .setKey("top_level")
                .setCategoryVelocityVarName("top_level_categoria_1")
                .nextPersisted();

        final Category childCategory_1 = new CategoryDataGen().setCategoryName("Child Category 1")
                .setKey("A")
                .setCategoryVelocityVarName("child_category_1")
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category childCategory_2 = new CategoryDataGen().setCategoryName("Child Category 2")
                .setKey("C")
                .setCategoryVelocityVarName("child_category_2")
                .parent(topLevelCategory_1)
                .nextPersisted();

        final Category grandchildCategory_1 = new CategoryDataGen().setCategoryName("Grand Child Category 1")
                .setKey("B")
                .setCategoryVelocityVarName("grand_child_category_1")
                .parent(childCategory_2)
                .nextPersisted();

        final CategoryFactory.CategorySearchCriteria categorySearchCriteria = new CategoryFactory.CategorySearchCriteria.Builder()
                .orderBy("category_key")
                .direction(OrderDirection.ASC)
                .rootInode(topLevelCategory_1.getInode())
                .build();

        final List<String> categoriesInode = FactoryLocator.getCategoryFactory().findAll(categorySearchCriteria)
            .stream().map(Category::getInode).collect(Collectors.toList());

        List<String> categoriesExpected = list(childCategory_1, grandchildCategory_1, childCategory_2).stream()
                .map(Category::getInode).collect(Collectors.toList());

        assertEquals(categoriesExpected.size(), categoriesInode.size());

        for (int i =0; i < categoriesExpected.size(); i++){
            assertEquals(categoriesExpected.get(i), categoriesInode.get(i));
        }

    }

    private static class FilterTestCase {
        private String filter;
        private Function<String, String> transformToSearch;

        public FilterTestCase(final String filter, final Function<String, String> transformToSearch) {
            this.filter = filter;
            this.transformToSearch = transformToSearch;
        }

        public String transformToSearch(){
            return transformToSearch.apply(filter);
        }
    }
}
